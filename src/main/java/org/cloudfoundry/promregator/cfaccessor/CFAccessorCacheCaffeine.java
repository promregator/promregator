package org.cloudfoundry.promregator.cfaccessor;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationProcessesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CFAccessorCacheCaffeine implements CFAccessorCache {
	private static final Logger log = LoggerFactory.getLogger(CFAccessorCacheCaffeine.class);

	private @NonNull AsyncLoadingCache<String, ListOrganizationsResponse> orgCache;
	private @NonNull AsyncLoadingCache<String, ListOrganizationsResponse> allOrgIdCache;
	private @NonNull AsyncLoadingCache<CacheKeySpace, ListSpacesResponse> spaceCache;
	private @NonNull AsyncLoadingCache<String, ListSpacesResponse> spaceIdInOrgCache;
	private @NonNull AsyncLoadingCache<String, ListOrganizationDomainsResponse> domainsInOrgCache;
	private @NonNull AsyncLoadingCache<CacheKeyAppsInSpace, ListApplicationsResponse> appsInSpaceCache;
	private @NonNull AsyncLoadingCache<String, ListRoutesResponse> routesCache;
	private @NonNull AsyncLoadingCache<String, ListApplicationProcessesResponse> webProcessCache;
	
	@Value("${cf.cache.timeout.org:3600}")
	private int refreshCacheOrgLevelInSeconds;

	@Value("${cf.cache.timeout.space:3600}")
	private int refreshCacheSpaceLevelInSeconds;
	
	@Value("${cf.cache.timeout.application:300}")
	private int refreshCacheApplicationLevelInSeconds;

	@Value("${cf.cache.timeout.domain:3600}")
	private int refreshCacheDomainLevelInSeconds;
		
	@Value("${cf.cache.expiry.org:120}")
	private int expiryCacheOrgLevelInSeconds;

	@Value("${cf.cache.expiry.space:120}")
	private int expiryCacheSpaceLevelInSeconds;
	
	@Value("${cf.cache.expiry.application:120}")
	private int expiryCacheApplicationLevelInSeconds;
	
	@Value("${cf.cache.expiry.domain:300}")
	private int expiryCacheDomainLevelInSeconds;
	
	@Autowired
	private InternalMetrics internalMetrics;

	
	private CFAccessor parent;

	
	
	public CFAccessorCacheCaffeine(CFAccessor parent) {
		this.parent = parent;
	}
	
	private class OrgCacheLoader implements AsyncCacheLoader<String, ListOrganizationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			
			Mono<ListOrganizationsResponse> mono = parent.retrieveOrgIdV3(key)
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class AllOrgIdCacheLoader implements AsyncCacheLoader<String, ListOrganizationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			
			Mono<ListOrganizationsResponse> mono = parent.retrieveAllOrgIdsV3()
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceCacheLoader implements AsyncCacheLoader<CacheKeySpace, ListSpacesResponse> {
		@Override
		public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull CacheKeySpace key,
				@NonNull Executor executor) {
			Mono<ListSpacesResponse> mono = parent.retrieveSpaceIdV3(key.getOrgId(), key.getSpaceName())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceIdInOrgCacheLoader implements AsyncCacheLoader<String, ListSpacesResponse> {
		@Override
		public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			Mono<ListSpacesResponse> mono = parent.retrieveSpaceIdsInOrgV3(key)
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}

	private class DomainCacheLoader implements AsyncCacheLoader<String, ListOrganizationDomainsResponse> {
		@Override
		public @NonNull CompletableFuture<ListOrganizationDomainsResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			Mono<ListOrganizationDomainsResponse> mono = parent.retrieveAllDomainsV3(key)
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}

	private class AppsInSpaceV3CacheLoader implements AsyncCacheLoader<CacheKeyAppsInSpace, ListApplicationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListApplicationsResponse> asyncLoad(
			@NonNull CacheKeyAppsInSpace key, @NonNull Executor executor) {
			Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationsInSpaceV3(key.getOrgId(), key.getSpaceId())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class RoutesCacheLoader implements AsyncCacheLoader<String, ListRoutesResponse> {
		@Override
		public @NonNull CompletableFuture<ListRoutesResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			/* TODO V3 Performance: 
			 * Not just pass on the request here, but use mass-enabled request at CAPI V3 endpoint
			 * by adding the request to a deque that is being polled from an independent thread.
			 * This thread may "aggregate" multiple single requests into a mass request.
			 */
			
			Mono<ListRoutesResponse> mono = parent.retrieveRoutesForAppIds(Collections.singleton(key))
				.subscribeOn(Schedulers.fromExecutor(executor))
				.cache();
			
			return mono.toFuture();
		}
	}
	
	private class WebProcessCacheLoader implements AsyncCacheLoader<String, ListApplicationProcessesResponse> {
		@Override
		public @NonNull CompletableFuture<ListApplicationProcessesResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			
			/* TODO V3 Performance: 
			 * Not just pass on the request here, but use mass-enabled request at CAPI V3 endpoint
			 * by adding the request to a deque that is being polled from an independent thread.
			 * This thread may "aggregate" multiple single requests into a mass request.
			 */
			
			Mono<ListApplicationProcessesResponse> mono = parent.retrieveWebProcessesForApp(key)
				.subscribeOn(Schedulers.fromExecutor(executor))
				.cache();
			
			return mono.toFuture();
		}
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
				.buildAsync(new OrgCacheLoader());
		this.internalMetrics.addCaffeineCache("orgCache", this.orgCache);
		
		this.allOrgIdCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AllOrgIdCacheLoader());
		this.internalMetrics.addCaffeineCache("allOrgCache", this.allOrgIdCache);
		
		this.spaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new SpaceCacheLoader());
		this.internalMetrics.addCaffeineCache("spaceCache", this.spaceCache);
		
		this.spaceIdInOrgCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new SpaceIdInOrgCacheLoader());
		this.internalMetrics.addCaffeineCache("spaceInOrgCache", this.spaceIdInOrgCache);

		this.domainsInOrgCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheDomainLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheDomainLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new DomainCacheLoader());
		this.internalMetrics.addCaffeineCache("domain", this.domainsInOrgCache);

		this.appsInSpaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AppsInSpaceV3CacheLoader());
		this.internalMetrics.addCaffeineCache("appsInSpace", this.appsInSpaceCache);
		
		this.routesCache = Caffeine.newBuilder()
//				TODO V3: Proper configuration options .expireAfterAccess(this.expiryCacheDomainLevelInSeconds, TimeUnit.SECONDS)
//				TODO V3: Proper configuration options .refreshAfterWrite(this.refreshCacheDomainLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new RoutesCacheLoader());
		this.internalMetrics.addCaffeineCache("routes", this.routesCache);

		this.webProcessCache = Caffeine.newBuilder()
//				TODO V3: Proper configuration options .expireAfterAccess(this.expiryCacheDomainLevelInSeconds, TimeUnit.SECONDS)
//				TODO V3: Proper configuration options .refreshAfterWrite(this.refreshCacheDomainLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new WebProcessCacheLoader());
		this.internalMetrics.addCaffeineCache("webprocess", this.webProcessCache);

		
	}

	@Override
	public Mono<GetInfoResponse> getInfo() {
		return this.parent.getInfo();
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		return Mono.fromFuture(this.orgCache.get(orgName));
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		return Mono.fromFuture(this.allOrgIdCache.get("all"));
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		
		return Mono.fromFuture(this.spaceCache.get(key));
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		return Mono.fromFuture(this.spaceIdInOrgCache.get(orgId));
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
		final CacheKeyAppsInSpace key = new CacheKeyAppsInSpace(orgId, spaceId);

		return Mono.fromFuture(this.appsInSpaceCache.get(key));
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		return Mono.fromFuture(this.domainsInOrgCache.get(orgId));
	}

	@Override
	public Mono<ListApplicationProcessesResponse> retrieveWebProcessesForApp(String applicationId) {
		return Mono.fromFuture(this.webProcessCache.get(applicationId));
	}
	
	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId) {
		return Mono.fromFuture(this.routesCache.get(appId));
	}
	
	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds) {
		/* Caching multiple IDs would cause a major problem in blocking properly
		 * on the Caffeine cache.
		 * However, we also don't need this implementation here, so we are just
		 * forwarding the request to the parent CFAccessor.
		 */
		return this.parent.retrieveRoutesForAppIds(appIds);
	}

	
	
	@Override
	public void invalidateCacheApplication() {
		log.info("Invalidating application cache");
		this.appsInSpaceCache.synchronous().invalidateAll();
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

	@Override
	public void invalidateCacheDomain() {
		log.info("Invalidating domain cache");
		this.domainsInOrgCache.synchronous().invalidateAll();
	}

	@Override
	public void invalidateCacheRoute() {
		log.info("Invalidating route cache");
		this.routesCache.synchronous().invalidateAll();
	}

	@Override
	public void invalidateCacheWebProcess() {
		log.info("Invalidating web process cache");
		this.webProcessCache.synchronous().invalidateAll();
	}

	@Override
	public void reset() {
		this.parent.reset();
	}
}
