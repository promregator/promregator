package org.cloudfoundry.promregator.cfaccessor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.cfaccessor.client.InfoV3;
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

	private AsyncLoadingCache<String, ListOrganizationsResponse> orgCache;
	private AsyncLoadingCache<String, ListOrganizationsResponse> allOrgIdCache;
	private AsyncLoadingCache<CacheKeySpace, ListSpacesResponse> spaceCache;
	private AsyncLoadingCache<String, ListSpacesResponse> spaceIdInOrgCache;
	private AsyncLoadingCache<String, ListOrganizationDomainsResponse> domainsInOrgCache;
	private AsyncLoadingCache<CacheKeyAppsInSpace, ListApplicationsResponse> appsInSpaceCache;
	private AsyncLoadingCache<String, ListRoutesResponse> routesCache;
	private AsyncLoadingCache<String, ListProcessesResponse> processCache;
	
	private RoutesRequestAggregator routesRequestAggregator;
	private ProcessRequestAggregator processRequestAggregator;
	
	@Value("${cf.cache.timeout.org:3600}")
	private int refreshCacheOrgLevelInSeconds;

	@Value("${cf.cache.timeout.space:3600}")
	private int refreshCacheSpaceLevelInSeconds;
	
	@Value("${cf.cache.timeout.application:300}")
	private int refreshCacheApplicationLevelInSeconds;

	@Value("${cf.cache.timeout.domain:3600}")
	private int refreshCacheDomainLevelInSeconds;
	
	@Value("${cf.cache.timeout.route:300}")
	private int refreshCacheRouteLevelInSeconds;
	
	@Value("${cf.cache.timeout.process:300}")
	private int refreshCacheProcessLevelInSeconds;
	
	@Value("${cf.cache.expiry.org:120}")
	private int expiryCacheOrgLevelInSeconds;

	@Value("${cf.cache.expiry.space:120}")
	private int expiryCacheSpaceLevelInSeconds;
	
	@Value("${cf.cache.expiry.application:120}")
	private int expiryCacheApplicationLevelInSeconds;
	
	@Value("${cf.cache.expiry.domain:300}")
	private int expiryCacheDomainLevelInSeconds;
	
	@Value("${cf.cache.expiry.route:120}")
	private int expiryCacheRouteLevelInSeconds;
	
	@Value("${cf.cache.expiry.process:120}")
	private int expiryCacheProcessLevelInSeconds;

	@Value("${cf.cache.aggregator.blocksize.route:100}")
	private int aggregatorBlocksizeRoute;
	
	@Value("${cf.cache.aggregator.checkinterval.route:125}")
	private int aggregatorCheckintervalRoute;

	@Value("${cf.cache.aggregator.blocksize.process:100}")
	private int aggregatorBlocksizeProcess;
	
	@Value("${cf.cache.aggregator.checkinterval.process:125}")
	private int aggregatorCheckintervalProcess;
	
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
	
	private class RoutesRequestAggregator extends RequestAggregator<String, ListRoutesResponse> {

		public RoutesRequestAggregator() {
			super(RequestAggregator.Type.ROUTE, internalMetrics, String.class, ListRoutesResponse.class, aggregatorCheckintervalRoute, aggregatorBlocksizeRoute);
		}

		@Override
		protected Mono<ListRoutesResponse> sendRequest(List<String> block) {
			return parent.retrieveRoutesForAppIds(new HashSet<>(block));
		}

		@Override
		protected Map<String, ListRoutesResponse> determineMapOfResponses(ListRoutesResponse response) {
			if (response == null || response.getResources() == null) {
				return Collections.emptyMap();
			}
			
			Map<String, List<RouteResource>> map = new HashMap<>();
			
			response.getResources().forEach(rr -> 
				rr.getDestinations().forEach(dest -> {
					String appId = dest.getApplication().getApplicationId();
					map.compute(appId, (key, lrr) -> {
						if (lrr == null) {
							lrr = new LinkedList<>();
						}
						lrr.add(rr);
						return lrr;
					});
				})
			);
			
			Map<String, ListRoutesResponse> resultMap = new HashMap<>();
			map.forEach((k, lrr) -> {
				ListRoutesResponse fullLrr = ListRoutesResponse.builder().resources(lrr).build();
				resultMap.put(k, fullLrr);
			});
			
			return resultMap;
		}
		
	}
	
	private class RoutesCacheLoader implements AsyncCacheLoader<String, ListRoutesResponse> {
		@Override
		public @NonNull CompletableFuture<ListRoutesResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			
			CompletableFuture<ListRoutesResponse> future = new CompletableFuture<>();
			routesRequestAggregator.addToQueue(key, future);
			return future;
		}
	}
	
	private class ProcessRequestAggregator extends RequestAggregator<String, ListProcessesResponse> {

		public ProcessRequestAggregator() {
			super(RequestAggregator.Type.PROCESS, internalMetrics, String.class, ListProcessesResponse.class, aggregatorCheckintervalProcess, aggregatorBlocksizeProcess);
		}

		@Override
		protected Mono<ListProcessesResponse> sendRequest(List<String> block) {
			return parent.retrieveWebProcessesForAppIds(new HashSet<>(block));
		}

		@Override
		protected Map<String, ListProcessesResponse> determineMapOfResponses(ListProcessesResponse response) {
			if (response == null || response.getResources() == null) {
				return Collections.emptyMap();
			}
			
			Map<String, List<ProcessResource>> map = new HashMap<>();
			response.getResources().forEach(pr -> {
				String appId = pr.getRelationships().getApp().getData().getId();
				map.compute(appId, (key, lpr) -> {
					if (lpr == null) {
						lpr = new LinkedList<>();
					}
					lpr.add(pr);
					return lpr;
				});
			});
			
			Map<String, ListProcessesResponse> resultMap = new HashMap<>();
			map.forEach((k, lpr) -> {
				ListProcessesResponse fullLrr = ListProcessesResponse.builder().resources(lpr).build();
				resultMap.put(k, fullLrr);
			});
			
			return resultMap;
		}

	}
	
	private class ProcessCacheLoader implements AsyncCacheLoader<String, ListProcessesResponse> {
		@Override
		public @NonNull CompletableFuture<ListProcessesResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			
			CompletableFuture<ListProcessesResponse> future = new CompletableFuture<>();
			processRequestAggregator.addToQueue(key, future);
			return future;
		}
	}
	
	@PostConstruct
	public void setupCaches() {
		log.info("Cache refresh timings: org cache: {}s, space cache: {}s, app cache: {}s, app cache: {}s, domain cache: {}s, route cache: {}s, process cache: {}s", 
				this.refreshCacheOrgLevelInSeconds, this.refreshCacheSpaceLevelInSeconds, this.refreshCacheApplicationLevelInSeconds, this.refreshCacheApplicationLevelInSeconds, this.refreshCacheDomainLevelInSeconds, this.refreshCacheRouteLevelInSeconds, this.refreshCacheProcessLevelInSeconds);
		log.info("Cache expiry timings: org cache: {}s, space cache: {}s, app cache: {}s, app cache: {}s, domain cache: {}s, route cache: {}s, process cache: {}s", 
				this.expiryCacheOrgLevelInSeconds, this.expiryCacheSpaceLevelInSeconds, this.expiryCacheApplicationLevelInSeconds, this.expiryCacheApplicationLevelInSeconds, this.expiryCacheDomainLevelInSeconds, this.expiryCacheRouteLevelInSeconds, this.expiryCacheProcessLevelInSeconds);
		
		Scheduler caffeineScheduler = Scheduler.forScheduledExecutorService(new ScheduledThreadPoolExecutor(1));
		
		this.routesRequestAggregator = new RoutesRequestAggregator();
		this.processRequestAggregator = new ProcessRequestAggregator();
		
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
				.expireAfterAccess(this.expiryCacheRouteLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheRouteLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new RoutesCacheLoader());
		this.internalMetrics.addCaffeineCache("routes", this.routesCache);

		this.processCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheProcessLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheProcessLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new ProcessCacheLoader());
		this.internalMetrics.addCaffeineCache("process", this.processCache);
		
	}

	@Override
	public Mono<InfoV3> getInfo() {
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
	public Mono<ListProcessesResponse> retrieveWebProcessesForAppId(String applicationId) {
		return Mono.fromFuture(this.processCache.get(applicationId));
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
	public Mono<ListProcessesResponse> retrieveWebProcessesForAppIds(Set<String> applicationIds) {
		/* Caching multiple IDs would cause a major problem in blocking properly
		 * on the Caffeine cache.
		 * However, we also don't need this implementation here, so we are just
		 * forwarding the request to the parent CFAccessor.
		 */
		return this.parent.retrieveWebProcessesForAppIds(applicationIds);
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
	public void invalidateCacheProcess() {
		log.info("Invalidating process cache");
		this.processCache.synchronous().invalidateAll();
	}

	@Override
	public void reset() {
		this.parent.reset();
	}

}
