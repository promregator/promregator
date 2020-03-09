package org.cloudfoundry.promregator.cfaccessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.config.CacheConfig;
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CFAccessorCacheCaffeine implements CFAccessorCache {
	private static final Logger log = LoggerFactory.getLogger(CFAccessorCacheCaffeine.class);

	private AsyncLoadingCache<CacheKeyOrganization, ListOrganizationsResponse> orgCache;
	private AsyncLoadingCache<String, ListOrganizationsResponse> allOrgIdCache;
	private AsyncLoadingCache<CacheKeySpace, ListSpacesResponse> spaceCache;
	private AsyncLoadingCache<CacheKeyOrganization, ListSpacesResponse> spaceIdInOrgCache;
	private AsyncLoadingCache<CacheKeyAppsInSpace, ListApplicationsResponse> appsInSpaceCache;
	private AsyncLoadingCache<CacheKeySpaceSummary, GetSpaceSummaryResponse> spaceSummaryCache;

	private final InternalMetrics internalMetrics;
	private final CFAccessor parent;
	private final CloudFoundryConfiguration cf;

	public CFAccessorCacheCaffeine(InternalMetrics internalMetrics,
								   CFAccessor parent,
								   CloudFoundryConfiguration cf) {
		this.internalMetrics = internalMetrics;
		this.parent = parent;
		this.cf = cf;
		setupCaches();
	}
	
	private class OrgCacheLoader implements AsyncCacheLoader<CacheKeyOrganization, ListOrganizationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull CacheKeyOrganization key,
				@NonNull Executor executor) {
			
			Mono<ListOrganizationsResponse> mono = parent.retrieveOrgId(key.getApi(), key.getOrg())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class AllOrgIdCacheLoader implements  AsyncCacheLoader<String, ListOrganizationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull String api,
				@NonNull Executor executor) {
			
			Mono<ListOrganizationsResponse> mono = parent.retrieveAllOrgIds(api)
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceCacheLoader implements AsyncCacheLoader<CacheKeySpace, ListSpacesResponse> {
		@Override
		public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull CacheKeySpace key,
				@NonNull Executor executor) {
			Mono<ListSpacesResponse> mono = parent.retrieveSpaceId(key.getApi(), key.getOrgId(), key.getSpaceName())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceIdInOrgCacheLoader implements AsyncCacheLoader<CacheKeyOrganization, ListSpacesResponse> {
		@Override
		public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull CacheKeyOrganization key,
				@NonNull Executor executor) {
			Mono<ListSpacesResponse> mono = parent.retrieveSpaceIdsInOrg(key.getApi(), key.getOrg())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class AppsInSpaceCacheLoader implements AsyncCacheLoader<CacheKeyAppsInSpace, ListApplicationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListApplicationsResponse> asyncLoad(
				@NonNull CacheKeyAppsInSpace key, @NonNull Executor executor) {
			Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationIdsInSpace(key.getApi(), key.getOrgId(), key.getSpaceId())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceSummaryCacheLoader implements AsyncCacheLoader<CacheKeySpaceSummary, GetSpaceSummaryResponse> {
		@Override
		public @NonNull CompletableFuture<GetSpaceSummaryResponse> asyncLoad(@NonNull CacheKeySpaceSummary key,
				@NonNull Executor executor) {
			Mono<GetSpaceSummaryResponse> mono = parent.retrieveSpaceSummary(key.getApi(), key.getSpaceId())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	public void setupCaches() {
		CacheConfig cacheConfig = cf.getCache();

		log.info(String.format("Cache refresh timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds",
				cacheConfig.getTimeout().getOrg(), cacheConfig.getTimeout().getSpace(),cacheConfig.getTimeout().getApplication(), cacheConfig.getTimeout().getApplication()));
		log.info(String.format("Cache expiry timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds",
				cacheConfig.getExpiry().getOrg(), cacheConfig.getExpiry().getSpace(),cacheConfig.getExpiry().getApplication(), cacheConfig.getExpiry().getApplication()));
		
		Scheduler caffeineScheduler = Scheduler.forScheduledExecutorService(new ScheduledThreadPoolExecutor(1));
		
		this.orgCache = Caffeine.newBuilder()
				.expireAfterAccess(cacheConfig.getExpiry().getOrg(), TimeUnit.SECONDS)
				.refreshAfterWrite(cacheConfig.getTimeout().getOrg(), TimeUnit.SECONDS)
				.scheduler(caffeineScheduler)
				.recordStats()
				.buildAsync(new OrgCacheLoader());
		this.internalMetrics.addCaffeineCache("orgCache", this.orgCache);
		
		this.allOrgIdCache = Caffeine.newBuilder()
				.expireAfterAccess(cacheConfig.getExpiry().getOrg(), TimeUnit.SECONDS)
				.refreshAfterWrite(cacheConfig.getTimeout().getOrg(), TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AllOrgIdCacheLoader());
		this.internalMetrics.addCaffeineCache("allOrgCache", this.allOrgIdCache);
		
		this.spaceCache = Caffeine.newBuilder()
				.expireAfterAccess(cacheConfig.getExpiry().getSpace(), TimeUnit.SECONDS)
				.refreshAfterWrite(cacheConfig.getTimeout().getSpace(), TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new SpaceCacheLoader());
		this.internalMetrics.addCaffeineCache("spaceCache", this.spaceCache);
		
		this.spaceIdInOrgCache = Caffeine.newBuilder()
				.expireAfterAccess(cacheConfig.getExpiry().getSpace(), TimeUnit.SECONDS)
				.refreshAfterWrite(cacheConfig.getTimeout().getSpace(), TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new SpaceIdInOrgCacheLoader());
		this.internalMetrics.addCaffeineCache("spaceInOrgCache", this.spaceIdInOrgCache);
		
		this.appsInSpaceCache = Caffeine.newBuilder()
				.expireAfterAccess(cacheConfig.getExpiry().getApplication(), TimeUnit.SECONDS)
				.refreshAfterWrite(cacheConfig.getTimeout().getApplication(), TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AppsInSpaceCacheLoader());
		this.internalMetrics.addCaffeineCache("appsInSpace", this.appsInSpaceCache);
		
		this.spaceSummaryCache = Caffeine.newBuilder()
				.expireAfterAccess(cacheConfig.getExpiry().getApplication(), TimeUnit.SECONDS)
				.refreshAfterWrite(cacheConfig.getTimeout().getApplication(), TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new SpaceSummaryCacheLoader());
		this.internalMetrics.addCaffeineCache("spaceSummary", this.spaceSummaryCache);
	}

	@Override
	public Mono<GetInfoResponse> getInfo(String api) {
		return this.parent.getInfo(api);
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String api, String orgName) {
		final CacheKeyOrganization key = new CacheKeyOrganization(api, orgName);

		return Mono.fromFuture(this.orgCache.get(key));
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String api, String orgId, String spaceName) {
		
		final CacheKeySpace key = new CacheKeySpace(api, orgId, spaceName);
		
		return Mono.fromFuture(this.spaceCache.get(key));
	}



	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String api, String orgId, String spaceId) {
		final CacheKeyAppsInSpace key = new CacheKeyAppsInSpace(api, orgId, spaceId);
		
		return Mono.fromFuture(this.appsInSpaceCache.get(key));
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String api, String spaceId) {
		final CacheKeySpaceSummary key = new CacheKeySpaceSummary(api, spaceId);

		return Mono.fromFuture(this.spaceSummaryCache.get(key));
	}

	
	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds(String api) {
		return Mono.fromFuture(this.allOrgIdCache.get(api));
	}
	
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String api, String orgId) {
		final CacheKeyOrganization key = new CacheKeyOrganization(api, orgId);

		return Mono.fromFuture(this.spaceIdInOrgCache.get(key));
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
		this.spaceIdInOrgCache.synchronous().invalidateAll();
	}

	@Override
	public void invalidateCacheOrg() {
		log.info("Invalidating org cache");
		this.orgCache.synchronous().invalidateAll();
		this.allOrgIdCache.synchronous().invalidateAll();
	}


}
