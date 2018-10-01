package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.GetApplicationResponse;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstancesResponse;
import org.cloudfoundry.promregator.cache.AutoRefreshingCacheMap;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Mono;

public class CFAccessorCache implements CFAccessor {
	private static final Logger log = Logger.getLogger(CFAccessorCache.class);

	private AutoRefreshingCacheMap<String, Mono<ListOrganizationsResponse>> orgCache;
	private AutoRefreshingCacheMap<CacheKeySpace, Mono<ListSpacesResponse>> spaceCache;
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
		this.spaceSummaryCache = new AutoRefreshingCacheMap<>("spaceSummary", this.internalMetrics, Duration.ofSeconds(this.expiryCacheApplicationLevelInSeconds), Duration.ofSeconds(refreshCacheApplicationLevelInSeconds), this::spaceSummaryCacheLoader);
	}

	private Mono<ListOrganizationsResponse> orgCacheLoader(String orgName) {
		Mono<ListOrganizationsResponse> mono = this.parent.retrieveOrgId(orgName);
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}
	
	private Mono<ListSpacesResponse> spaceCacheLoader(CacheKeySpace cacheKey) {
		Mono<ListSpacesResponse> mono = this.parent.retrieveSpaceId(cacheKey.getOrgId(), cacheKey.getSpaceName());
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}
	
	private Mono<GetSpaceSummaryResponse> spaceSummaryCacheLoader(String spaceId) {
		Mono<GetSpaceSummaryResponse> mono = this.parent.retrieveSpaceSummary(spaceId);
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		Mono<ListOrganizationsResponse> mono = this.orgCache.get(orgName);

		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		
		Mono<ListSpacesResponse> mono = this.spaceCache.get(key);
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		/*
		 * special case: we don't cache the result here in an own cache,
		 * as we always want to have "fresh data".
		 */
		Mono<ListApplicationsResponse> result = this.parent.retrieveAllApplicationIdsInSpace(orgId, spaceId);
		
		return result;
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

	@Override
	public Mono<ListUserProvidedServiceInstancesResponse> retrieveAllUserProvidedService() {
		// TODO: clarify caching
		return this.parent.retrieveAllUserProvidedService();
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpace(String spaceId) {
		// TODO: clarify caching
		return this.parent.retrieveSpace(spaceId);
	}

	@Override
	public Mono<GetOrganizationResponse> retrieveOrg(String orgId) {
		// TODO: clarify caching
		return this.parent.retrieveOrg(orgId);
	}

	@Override
	public Mono<ListUserProvidedServiceInstanceServiceBindingsResponse> retrieveUserProvidedServiceBindings(
			String upsId) {
		// TODO clarify caching
		return this.parent.retrieveUserProvidedServiceBindings(upsId);
	}

	@Override
	public Mono<GetApplicationResponse> retrieveApplication(String applicationId) {
		// TODO clarify caching
		return this.parent.retrieveApplication(applicationId);
	}

}
