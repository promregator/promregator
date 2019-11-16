package org.cloudfoundry.promregator.cfaccessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;

import net.javacrumbs.futureconverter.java8guava.FutureConverter;
import reactor.core.publisher.Mono;

public class CFAccessorCacheGuava implements CFAccessorCache {
	private static final Logger log = Logger.getLogger(CFAccessorCacheGuava.class);

	private LoadingCache<String, ListOrganizationsResponse> orgCache;
	private LoadingCache<CacheKeySpace, ListSpacesResponse> spaceCache;
	private LoadingCache<CacheKeyAppsInSpace, ListApplicationsResponse> appsInSpaceCache;
	private LoadingCache<String, GetSpaceSummaryResponse> spaceSummaryCache;
	
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
	
	
	private CFAccessor parent;
	
	public CFAccessorCacheGuava(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupCaches() {
		this.orgCache = CacheBuilder.newBuilder()
				.expireAfterAccess(this.expiryCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.build(new CacheLoader<String, ListOrganizationsResponse>() {

					@Override
					public ListOrganizationsResponse load(String key) throws Exception {
						Mono<ListOrganizationsResponse> mono = parent.retrieveOrgId(key);
						return mono.block();
					}

					/* (non-Javadoc)
					 * @see com.google.common.cache.CacheLoader#reload(java.lang.Object, java.lang.Object)
					 */
					@Override
					public ListenableFuture<ListOrganizationsResponse> reload(String key,
							ListOrganizationsResponse oldValue) throws Exception {
						
						Mono<ListOrganizationsResponse> mono = parent.retrieveOrgId(key);
						CompletableFuture<ListOrganizationsResponse> future = mono.toFuture();
						// see also https://github.com/google/guava/issues/2350#issuecomment-169097253
						return FutureConverter.toListenableFuture(future);
					}
				});
		// TODO: handling of cache statistics via metrics!
		
		
		this.spaceCache = CacheBuilder.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.build(new CacheLoader<CacheKeySpace, ListSpacesResponse>() {

					@Override
					public ListSpacesResponse load(CacheKeySpace key) throws Exception {
						Mono<ListSpacesResponse> mono = parent.retrieveSpaceId(key.getOrgId(), key.getSpaceName());
						return mono.block();
					}

					/* (non-Javadoc)
					 * @see com.google.common.cache.CacheLoader#reload(java.lang.Object, java.lang.Object)
					 */
					@Override
					public ListenableFuture<ListSpacesResponse> reload(CacheKeySpace key,
							ListSpacesResponse oldValue) throws Exception {
						
						Mono<ListSpacesResponse> mono = parent.retrieveSpaceId(key.getOrgId(), key.getSpaceName());
						CompletableFuture<ListSpacesResponse> future = mono.toFuture();
						// see also https://github.com/google/guava/issues/2350#issuecomment-169097253
						return FutureConverter.toListenableFuture(future);
					}
				});
		// TODO: handling of cache statistics via metrics!
		
		
		this.appsInSpaceCache = CacheBuilder.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.build(new CacheLoader<CacheKeyAppsInSpace, ListApplicationsResponse>() {

					@Override
					public ListApplicationsResponse load(CacheKeyAppsInSpace key) throws Exception {
						Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationIdsInSpace(key.getOrgId(), key.getSpaceId());
						return mono.block();
					}

					/* (non-Javadoc)
					 * @see com.google.common.cache.CacheLoader#reload(java.lang.Object, java.lang.Object)
					 */
					@Override
					public ListenableFuture<ListApplicationsResponse> reload(CacheKeyAppsInSpace key,
							ListApplicationsResponse oldValue) throws Exception {
						
						Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationIdsInSpace(key.getOrgId(), key.getSpaceId());
						CompletableFuture<ListApplicationsResponse> future = mono.toFuture();
						// see also https://github.com/google/guava/issues/2350#issuecomment-169097253
						return FutureConverter.toListenableFuture(future);
					}
				});
		// TODO: handling of cache statistics via metrics!
		
		
		this.spaceSummaryCache = CacheBuilder.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.build(new CacheLoader<String, GetSpaceSummaryResponse>() {

					@Override
					public GetSpaceSummaryResponse load(String key) throws Exception {
						Mono<GetSpaceSummaryResponse> mono = parent.retrieveSpaceSummary(key);
						return mono.block();
					}

					/* (non-Javadoc)
					 * @see com.google.common.cache.CacheLoader#reload(java.lang.Object, java.lang.Object)
					 */
					@Override
					public ListenableFuture<GetSpaceSummaryResponse> reload(String key,
							GetSpaceSummaryResponse oldValue) throws Exception {
						
						Mono<GetSpaceSummaryResponse> mono = parent.retrieveSpaceSummary(key);
						CompletableFuture<GetSpaceSummaryResponse> future = mono.toFuture();
						// see also https://github.com/google/guava/issues/2350#issuecomment-169097253
						return FutureConverter.toListenableFuture(future);
					}
				});
		// TODO: handling of cache statistics via metrics!
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		try {
			return Mono.just(this.orgCache.get(orgName));
		} catch (ExecutionException e) {
			return Mono.error(e);
		}
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		try {
			return Mono.just(this.spaceCache.get(key));
		} catch (ExecutionException e) {
			return Mono.error(e);
		}
	}



	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		final CacheKeyAppsInSpace key = new CacheKeyAppsInSpace(orgId, spaceId);
		
		try {
			return Mono.just(this.appsInSpaceCache.get(key));
		} catch (ExecutionException e) {
			return Mono.error(e);
		}
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		try {
			return Mono.just(this.spaceSummaryCache.get(spaceId));
		} catch (ExecutionException e) {
			return Mono.error(e);
		}
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
	public void invalidateCacheApplications() {
		log.info("Invalidating application cache");
		this.appsInSpaceCache.invalidateAll();
		this.spaceSummaryCache.invalidateAll();
	}

	@Override
	public void invalidateCacheSpace() {
		log.info("Invalidating space cache");
		this.spaceCache.invalidateAll();
	}

	@Override
	public void invalidateCacheOrg() {
		log.info("Invalidating org cache");
		this.orgCache.invalidateAll();
	}

}
