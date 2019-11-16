package org.cloudfoundry.promregator.cfaccessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import reactor.core.publisher.Mono;

public class CFAccessorCacheCaffeine implements CFAccessorCache {
	private static final Logger log = Logger.getLogger(CFAccessorCacheCaffeine.class);

	private AsyncLoadingCache<String, ListOrganizationsResponse> orgCache;
	private AsyncLoadingCache<CacheKeySpace, ListSpacesResponse> spaceCache;
	private AsyncLoadingCache<CacheKeyAppsInSpace, ListApplicationsResponse> appsInSpaceCache;
	private AsyncLoadingCache<String, GetSpaceSummaryResponse> spaceSummaryCache;
	
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
	
	public CFAccessorCacheCaffeine(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupCaches() {
		this.orgCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.buildAsync(new AsyncCacheLoader<String, ListOrganizationsResponse>() {

					@Override
					public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull String key,
							@NonNull Executor executor) {
						
						Mono<ListOrganizationsResponse> mono = parent.retrieveOrgId(key);
						return mono.toFuture();
					}
					

				});
		// TODO: handling of cache statistics via metrics!
		
		
		this.spaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.buildAsync(new AsyncCacheLoader<CacheKeySpace, ListSpacesResponse>() {

					@Override
					public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull CacheKeySpace key,
							@NonNull Executor executor) {
						Mono<ListSpacesResponse> mono = parent.retrieveSpaceId(key.getOrgId(), key.getSpaceName());
						return mono.toFuture();
					}
				});
		// TODO: handling of cache statistics via metrics!
		
		
		this.appsInSpaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.buildAsync(new AsyncCacheLoader<CacheKeyAppsInSpace, ListApplicationsResponse>() {

					@Override
					public @NonNull CompletableFuture<ListApplicationsResponse> asyncLoad(
							@NonNull CacheKeyAppsInSpace key, @NonNull Executor executor) {
						Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationIdsInSpace(key.getOrgId(), key.getSpaceId());
						return mono.toFuture();
					}
				});
		// TODO: handling of cache statistics via metrics!
		
		
		this.spaceSummaryCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.buildAsync(new AsyncCacheLoader<String, GetSpaceSummaryResponse>() {
					@Override
					public @NonNull CompletableFuture<GetSpaceSummaryResponse> asyncLoad(@NonNull String key,
							@NonNull Executor executor) {
						Mono<GetSpaceSummaryResponse> mono = parent.retrieveSpaceSummary(key).cache();
						return mono.toFuture();
					}
				});
		// TODO: handling of cache statistics via metrics!
	}
	
	@Scheduled(initialDelay=10000, fixedDelay=60*1000)
	public void guavaCacheCleanup() {
		this.orgCache.synchronous().cleanUp();
		this.spaceCache.synchronous().cleanUp();
		this.appsInSpaceCache.synchronous().cleanUp();
		this.spaceSummaryCache.synchronous().cleanUp();
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		return Mono.fromFuture(this.orgCache.get(orgName));
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		
		return Mono.fromFuture(this.spaceCache.get(key));
	}



	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		final CacheKeyAppsInSpace key = new CacheKeyAppsInSpace(orgId, spaceId);
		
		return Mono.fromFuture(this.appsInSpaceCache.get(key));
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		return Mono.fromFuture(this.spaceSummaryCache.get(spaceId));
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
		
		this.appsInSpaceCache.synchronous().invalidateAll();
		this.spaceSummaryCache.synchronous().invalidateAll();
	}

	@Override
	public void invalidateCacheSpace() {
		log.info("Invalidating space cache");
		this.spaceCache.synchronous().invalidateAll();
	}

	@Override
	public void invalidateCacheOrg() {
		log.info("Invalidating org cache");
		this.orgCache.synchronous().invalidateAll();
	}

}
