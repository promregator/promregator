package org.cloudfoundry.promregator.cfaccessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CFAccessorCacheCaffeine implements CFAccessorCache {
	private static final Logger log = Logger.getLogger(CFAccessorCacheCaffeine.class);

	private AsyncLoadingCache<String, ListOrganizationsResponse> orgCache;
	private AsyncLoadingCache<String, ListOrganizationsResponse> allOrgIdCache;
	private AsyncLoadingCache<CacheKeySpace, ListSpacesResponse> spaceCache;
	private AsyncLoadingCache<String, ListSpacesResponse> spaceIdInOrgCache;
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
	
	
	@Autowired
	private InternalMetrics internalMetrics;

	
	private CFAccessor parent;
	
	public CFAccessorCacheCaffeine(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupCaches() {
		log.info(String.format("Cache refresh timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds", 
				this.refreshCacheOrgLevelInSeconds, this.refreshCacheSpaceLevelInSeconds, this.refreshCacheApplicationLevelInSeconds, this.refreshCacheApplicationLevelInSeconds));
		log.info(String.format("Cache expiry timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds", 
				this.expiryCacheOrgLevelInSeconds, this.expiryCacheSpaceLevelInSeconds, this.expiryCacheApplicationLevelInSeconds, this.expiryCacheApplicationLevelInSeconds));

		
		Scheduler caffeineScheduler = Scheduler.forScheduledExecutorService(new ScheduledThreadPoolExecutor(1));
		
		this.orgCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.scheduler(caffeineScheduler)
				.recordStats()
				.buildAsync(new AsyncCacheLoader<String, ListOrganizationsResponse>() {

					@Override
					public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull String key,
							@NonNull Executor executor) {
						
						Mono<ListOrganizationsResponse> mono = parent.retrieveOrgId(key)
								.subscribeOn(Schedulers.fromExecutor(executor))
								.cache();
						return mono.toFuture();
					}
					

				});
		this.internalMetrics.addCaffeineCache("orgCache", this.orgCache);
		
		
		this.allOrgIdCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AsyncCacheLoader<String, ListOrganizationsResponse>() {

					@Override
					public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull String key,
							@NonNull Executor executor) {
						
						Mono<ListOrganizationsResponse> mono = parent.retrieveAllOrgIds()
								.subscribeOn(Schedulers.fromExecutor(executor))
								.cache();
						return mono.toFuture();
					}
					

				});
		this.internalMetrics.addCaffeineCache("allOrgCache", this.allOrgIdCache);
		
		this.spaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AsyncCacheLoader<CacheKeySpace, ListSpacesResponse>() {

					@Override
					public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull CacheKeySpace key,
							@NonNull Executor executor) {
						Mono<ListSpacesResponse> mono = parent.retrieveSpaceId(key.getOrgId(), key.getSpaceName())
								.subscribeOn(Schedulers.fromExecutor(executor))
								.cache();
						return mono.toFuture();
					}
				});
		this.internalMetrics.addCaffeineCache("spaceCache", this.spaceCache);
		
		this.spaceIdInOrgCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AsyncCacheLoader<String, ListSpacesResponse>() {

					@Override
					public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull String key,
							@NonNull Executor executor) {
						Mono<ListSpacesResponse> mono = parent.retrieveSpaceIdsInOrg(key)
								.subscribeOn(Schedulers.fromExecutor(executor))
								.cache();
						return mono.toFuture();
					}
				});
		this.internalMetrics.addCaffeineCache("spaceInOrgCache", this.spaceIdInOrgCache);
		
		
		this.appsInSpaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AsyncCacheLoader<CacheKeyAppsInSpace, ListApplicationsResponse>() {

					@Override
					public @NonNull CompletableFuture<ListApplicationsResponse> asyncLoad(
							@NonNull CacheKeyAppsInSpace key, @NonNull Executor executor) {
						Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationIdsInSpace(key.getOrgId(), key.getSpaceId())
								.subscribeOn(Schedulers.fromExecutor(executor))
								.cache();
						return mono.toFuture();
					}
				});
		this.internalMetrics.addCaffeineCache("appsInSpace", this.appsInSpaceCache);
		
		
		this.spaceSummaryCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AsyncCacheLoader<String, GetSpaceSummaryResponse>() {
					@Override
					public @NonNull CompletableFuture<GetSpaceSummaryResponse> asyncLoad(@NonNull String key,
							@NonNull Executor executor) {
						Mono<GetSpaceSummaryResponse> mono = parent.retrieveSpaceSummary(key)
								.subscribeOn(Schedulers.fromExecutor(executor))
								.cache();
						return mono.toFuture();
					}
				});
		this.internalMetrics.addCaffeineCache("spaceSummary", this.spaceSummaryCache);
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
		return Mono.fromFuture(this.allOrgIdCache.get("all"));
	}
	
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		return Mono.fromFuture(this.spaceIdInOrgCache.get(orgId));
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
