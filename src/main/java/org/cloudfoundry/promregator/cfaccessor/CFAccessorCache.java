package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.cache.AutoRefreshingCacheMap;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Mono;

public class CFAccessorCache implements CFAccessor {
	private static final Logger log = Logger.getLogger(CFAccessorCache.class);

	private AutoRefreshingCacheMap<String, Mono<ListOrganizationsResponse>> orgCache;
	private AutoRefreshingCacheMap<CacheKeySpace, Mono<ListSpacesResponse>> spaceCache;
	private AutoRefreshingCacheMap<CacheKeyAppsInSpace, Mono<ListApplicationsResponse>> appsInSpaceCache;
	private AutoRefreshingCacheMap<String, Mono<GetSpaceSummaryResponse>> spaceSummaryCache;
	
	@Value("${cf.cache.timeout.org:3600}")
	private int refreshCacheOrgLevelInSeconds;

	@Value("${cf.cache.timeout.space:3600}")
	private int refreshCacheSpaceLevelInSeconds;
	
	@Value("${cf.cache.timeout.application:300}")
	private int refreshCacheApplicationLevelInSeconds;
		
	@Value("${cf.cache.expiry.org:120}")
	private int expiryCacheOrgLevelInSeconds;

	@Value("${cf.cache.expiry.space:120}")
	private int expiryCacheSpaceLevelInSeconds;
	
	@Value("${cf.cache.expiry.application:120}")
	private int expiryCacheApplicationLevelInSeconds;
	
	@Autowired
	private InternalMetrics internalMetrics;
	
	private CFAccessor parent;
	
	public CFAccessorCache(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupMaps() {
		log.info(String.format("Cache refresh timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds", 
				this.refreshCacheOrgLevelInSeconds, this.refreshCacheSpaceLevelInSeconds, this.refreshCacheApplicationLevelInSeconds, this.refreshCacheApplicationLevelInSeconds));
		log.info(String.format("Cache expiry timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds", 
				this.expiryCacheOrgLevelInSeconds, this.expiryCacheSpaceLevelInSeconds, this.expiryCacheApplicationLevelInSeconds, this.expiryCacheApplicationLevelInSeconds));
		
		/*
		 * initializing caches
		 */
		this.orgCache = new AutoRefreshingCacheMap<>("org", this.internalMetrics, Duration.ofSeconds(this.expiryCacheOrgLevelInSeconds), Duration.ofSeconds(this.refreshCacheOrgLevelInSeconds), this::orgCacheLoader);
		this.spaceCache = new AutoRefreshingCacheMap<>("space", this.internalMetrics, Duration.ofSeconds(this.expiryCacheSpaceLevelInSeconds), Duration.ofSeconds(refreshCacheSpaceLevelInSeconds), this::spaceCacheLoader);
		this.appsInSpaceCache = new AutoRefreshingCacheMap<>("appsInSpace", this.internalMetrics, Duration.ofSeconds(this.expiryCacheApplicationLevelInSeconds), Duration.ofSeconds(refreshCacheApplicationLevelInSeconds), this::appsInSpaceCacheLoader);
		this.spaceSummaryCache = new AutoRefreshingCacheMap<>("spaceSummary", this.internalMetrics, Duration.ofSeconds(this.expiryCacheApplicationLevelInSeconds), Duration.ofSeconds(refreshCacheApplicationLevelInSeconds), this::spaceSummaryCacheLoader);
	}

	private Mono<ListOrganizationsResponse> orgCacheLoader(String orgName) {
		Mono<ListOrganizationsResponse> mono = this.parent.retrieveOrgId(orgName).cache();
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		
		/*
		 * Handling for issue #96: If a timeout of the request to the  CF Cloud Controller occurs, 
		 * we must make sure that the erroneous Mono is not kept in the cache. Instead we have to displace the item, 
		 * which triggers a refresh of the cache.
		 * 
		 * Note that subscribe() must be called *before* adding this error handling below.
		 * Otherwise we will run into the situation that the error handling routine is called by this
		 * subscribe() already - but the Mono has not been written into the cache yet!
		 * If we do it in this order, the doOnError method will only be called once the first "real subscriber" 
		 * of the Mono will start requesting.
		 */
		mono = mono.doOnError(e -> {
			if (e instanceof TimeoutException) {
				log.warn(String.format("Timed-out entry using key %s detected, which would get stuck in our org cache; "
						+ "displacing it now to prevent further harm", orgName), e);
				/* 
				 * Note that it *might* happen that a different Mono gets displaced than the one we are in here now. 
				 * Yet, we can't make use of the
				 * 
				 * remove(key, value)
				 * 
				 * method, as providing value would lead to a hen-egg problem (we were required to provide the reference
				 * of the Mono instance, which we are just creating).
				 * Instead, we just blindly remove the entry from the cache. This may lead to four cases to consider:
				 * 
				 * 1. We hit the correct (erroneous) entry: then this is exactly what we want to do.
				 * 2. We hit another erroneous entry: then we have no harm done, because we fixed yet another case.
				 * 3. We hit a healthy entry: Bad luck; on next iteration, we will get a cache miss, which automatically
				 *    fixes the issue (as long this does not happen too often, ...)
				 * 4. The entry has already been deleted by someone else: the remove(key) operation will 
				 *    simply be a NOOP. => no harm done either.
				 */
				this.orgCache.remove(orgName);
				
				// Notify metrics of this case
				if (this.internalMetrics != null) {
					this.internalMetrics.countAutoRefreshingCacheMapErroneousEntriesDisplaced(this.orgCache.getName());
				}
			}
		});
		/*
		 * Keep in mind that doOnError is a side-effect:  The logic above only removes it from the cache. 
		 * The erroneous instance still is used downstream and will trigger subsequent error handling (including 
		 * logging) there.
		 * Note that this also holds true during the timeframe of the timeout: This instance of the Mono will 
		 * be written to the cache, thus all consumers of the cache will be handed out the cached, not-yet-resolved 
		 * object instance. This implicitly makes sure that there can only be one valid pending request is out there.
		 */
		
		return mono;
	}
	
	private Mono<ListSpacesResponse> spaceCacheLoader(CacheKeySpace cacheKey) {
		Mono<ListSpacesResponse> mono = this.parent.retrieveSpaceId(cacheKey.getOrgId(), cacheKey.getSpaceName()).cache();
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		
		/*
		 * Handling for issue #96: If a timeout of the request to the  CF Cloud Controller occurs, 
		 * we must make sure that the erroneous Mono is not kept in the cache. Instead we have to displace the item, 
		 * which triggers a refresh of the cache.
		 * 
		 * Note that subscribe() must be called *before* adding this error handling below.
		 * Otherwise we will run into the situation that the error handling routine is called by this
		 * subscribe() already - but the Mono has not been written into the cache yet!
		 * If we do it in this order, the doOnError method will only be called once the first "real subscriber" 
		 * of the Mono will start requesting.
		 */
		mono = mono.doOnError(e -> {
			if (e instanceof TimeoutException) {
				log.warn(String.format("Timed-out entry using key %s detected, which would get stuck in our space cache; "
						+ "displacing it now to prevent further harm", cacheKey), e);
				/* 
				 * Note that it *might* happen that a different Mono gets displaced than the one we are in here now. 
				 * Yet, we can't make use of the
				 * 
				 * remove(key, value)
				 * 
				 * method, as providing value would lead to a hen-egg problem (we were required to provide the reference
				 * of the Mono instance, which we are just creating).
				 * Instead, we just blindly remove the entry from the cache. This may lead to four cases to consider:
				 * 
				 * 1. We hit the correct (erroneous) entry: then this is exactly what we want to do.
				 * 2. We hit another erroneous entry: then we have no harm done, because we fixed yet another case.
				 * 3. We hit a healthy entry: Bad luck; on next iteration, we will get a cache miss, which automatically
				 *    fixes the issue (as long this does not happen too often, ...)
				 * 4. The entry has already been deleted by someone else: the remove(key) operation will 
				 *    simply be a NOOP. => no harm done either.
				 */
				this.spaceCache.remove(cacheKey);
				
				// Notify metrics of this case
				if (this.internalMetrics != null) {
					this.internalMetrics.countAutoRefreshingCacheMapErroneousEntriesDisplaced(this.spaceCache.getName());
				}
			}
		});
		/*
		 * Keep in mind that doOnError is a side-effect:  The logic above only removes it from the cache. 
		 * The erroneous instance still is used downstream and will trigger subsequent error handling (including 
		 * logging) there.
		 * Note that this also holds true during the timeframe of the timeout: This instance of the Mono will 
		 * be written to the cache, thus all consumers of the cache will be handed out the cached, not-yet-resolved 
		 * object instance. This implicitly makes sure that there can only be one valid pending request is out there.
		 */

		return mono;
	}
	
	private Mono<ListApplicationsResponse> appsInSpaceCacheLoader(CacheKeyAppsInSpace cacheKey) {
		Mono<ListApplicationsResponse> mono = this.parent.retrieveAllApplicationIdsInSpace(cacheKey.getOrgId(), cacheKey.getSpaceId()).cache();
		
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		
		/*
		 * Handling for issue #96: If a timeout of the request to the  CF Cloud Controller occurs, 
		 * we must make sure that the erroneous Mono is not kept in the cache. Instead we have to displace the item, 
		 * which triggers a refresh of the cache.
		 * 
		 * Note that subscribe() must be called *before* adding this error handling below.
		 * Otherwise we will run into the situation that the error handling routine is called by this
		 * subscribe() already - but the Mono has not been written into the cache yet!
		 * If we do it in this order, the doOnError method will only be called once the first "real subscriber" 
		 * of the Mono will start requesting.
		 */
		mono = mono.doOnError(e -> {
			if (e instanceof TimeoutException) {
				log.warn(String.format("Timed-out entry using key %s detected, which would get stuck in our appsInSpace cache; "
						+ "displacing it now to prevent further harm", cacheKey), e);
				/* 
				 * Note that it *might* happen that a different Mono gets displaced than the one we are in here now. 
				 * Yet, we can't make use of the
				 * 
				 * remove(key, value)
				 * 
				 * method, as providing value would lead to a hen-egg problem (we were required to provide the reference
				 * of the Mono instance, which we are just creating).
				 * Instead, we just blindly remove the entry from the cache. This may lead to four cases to consider:
				 * 
				 * 1. We hit the correct (erroneous) entry: then this is exactly what we want to do.
				 * 2. We hit another erroneous entry: then we have no harm done, because we fixed yet another case.
				 * 3. We hit a healthy entry: Bad luck; on next iteration, we will get a cache miss, which automatically
				 *    fixes the issue (as long this does not happen too often, ...)
				 * 4. The entry has already been deleted by someone else: the remove(key) operation will 
				 *    simply be a NOOP. => no harm done either.
				 */
				this.appsInSpaceCache.remove(cacheKey);
				
				// Notify metrics of this case
				if (this.internalMetrics != null) {
					this.internalMetrics.countAutoRefreshingCacheMapErroneousEntriesDisplaced(this.appsInSpaceCache.getName());
				}
			}
		});
		/*
		 * Keep in mind that doOnError is a side-effect:  The logic above only removes it from the cache. 
		 * The erroneous instance still is used downstream and will trigger subsequent error handling (including 
		 * logging) there.
		 * Note that this also holds true during the timeframe of the timeout: This instance of the Mono will 
		 * be written to the cache, thus all consumers of the cache will be handed out the cached, not-yet-resolved 
		 * object instance. This implicitly makes sure that there can only be one valid pending request is out there.
		 */

		return mono;
	}
	
	private Mono<GetSpaceSummaryResponse> spaceSummaryCacheLoader(String spaceId) {
		Mono<GetSpaceSummaryResponse> mono = this.parent.retrieveSpaceSummary(spaceId).cache();
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		
		/*
		 * Handling for issue #96: If a timeout of the request to the  CF Cloud Controller occurs, 
		 * we must make sure that the erroneous Mono is not kept in the cache. Instead we have to displace the item, 
		 * which triggers a refresh of the cache.
		 * 
		 * Note that subscribe() must be called *before* adding this error handling below.
		 * Otherwise we will run into the situation that the error handling routine is called by this
		 * subscribe() already - but the Mono has not been written into the cache yet!
		 * If we do it in this order, the doOnError method will only be called once the first "real subscriber" 
		 * of the Mono will start requesting.
		 */
		mono = mono.doOnError(e -> {
			if (e instanceof TimeoutException) {
				log.warn(String.format("Timed-out entry using key %s detected, which would get stuck in our spaceSummary cache; "
						+ "displacing it now to prevent further harm", spaceId), e);
				/* 
				 * Note that it *might* happen that a different Mono gets displaced than the one we are in here now. 
				 * Yet, we can't make use of the
				 * 
				 * remove(key, value)
				 * 
				 * method, as providing value would lead to a hen-egg problem (we were required to provide the reference
				 * of the Mono instance, which we are just creating).
				 * Instead, we just blindly remove the entry from the cache. This may lead to four cases to consider:
				 * 
				 * 1. We hit the correct (erroneous) entry: then this is exactly what we want to do.
				 * 2. We hit another erroneous entry: then we have no harm done, because we fixed yet another case.
				 * 3. We hit a healthy entry: Bad luck; on next iteration, we will get a cache miss, which automatically
				 *    fixes the issue (as long this does not happen too often, ...)
				 * 4. The entry has already been deleted by someone else: the remove(key) operation will 
				 *    simply be a NOOP. => no harm done either.
				 */
				this.spaceSummaryCache.remove(spaceId);
				
				// Notify metrics of this case
				if (this.internalMetrics != null) {
					this.internalMetrics.countAutoRefreshingCacheMapErroneousEntriesDisplaced(this.spaceSummaryCache.getName());
				}
			}
		});
		/*
		 * Keep in mind that doOnError is a side-effect:  The logic above only removes it from the cache. 
		 * The erroneous instance still is used downstream and will trigger subsequent error handling (including 
		 * logging) there.
		 * Note that this also holds true during the timeframe of the timeout: This instance of the Mono will 
		 * be written to the cache, thus all consumers of the cache will be handed out the cached, not-yet-resolved 
		 * object instance. This implicitly makes sure that there can only be one valid pending request is out there.
		 */
		
		return mono;
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		Mono<ListOrganizationsResponse> mono = this.orgCache.get(orgName);

		return mono;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		
		// TODO Unclear if problem: locking in the cache works on object instance level! We just created a new instance there. Separate lock objects?
		Mono<ListSpacesResponse> mono = this.spaceCache.get(key);
		
		return mono;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		final CacheKeyAppsInSpace key = new CacheKeyAppsInSpace(orgId, spaceId);
		
		// TODO Unclear if problem: locking in the cache works on object instance level! We just created a new instance there. Separate lock objects?
		return this.appsInSpaceCache.get(key);
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		/*
		 * special case: we don't cache the result here in an own cache,
		 * as we always want to have "fresh data".
		 */
		return this.parent.retrieveAllOrgIds();
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		/*
		 * special case: we don't cache the result here in an own cache,
		 * as we always want to have "fresh data".
		 */
		return this.parent.retrieveSpaceIdsInOrg(orgId);
	}

	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		return this.spaceSummaryCache.get(spaceId);
	}

	public void invalidateCacheApplications() {
		log.info("Invalidating application cache");
		this.spaceSummaryCache.clear();
	}
	
	public void invalidateCacheSpace() {
		log.info("Invalidating space cache");
		this.spaceCache.clear();
	}

	public void invalidateCacheOrg() {
		log.info("Invalidating org cache");
		this.orgCache.clear();
	}

}
