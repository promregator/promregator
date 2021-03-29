package org.cloudfoundry.promregator.cfaccessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
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
	private AsyncLoadingCache<CacheKeyAppsInSpace, ListApplicationsResponse> appsInSpaceCache;
	private AsyncLoadingCache<String, GetSpaceSummaryResponse> spaceSummaryCache;		
	private AsyncLoadingCache<String, ListOrganizationDomainsResponse> domainsInOrgCache;	
	
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
			
			Mono<ListOrganizationsResponse> mono = parent.retrieveOrgId(key)
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class AllOrgIdCacheLoader implements  AsyncCacheLoader<String, ListOrganizationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListOrganizationsResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			
			Mono<ListOrganizationsResponse> mono = parent.retrieveAllOrgIds()
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceCacheLoader implements AsyncCacheLoader<CacheKeySpace, ListSpacesResponse> {
		@Override
		public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull CacheKeySpace key,
				@NonNull Executor executor) {
			Mono<ListSpacesResponse> mono = parent.retrieveSpaceId(key.getOrgId(), key.getSpaceName())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceIdInOrgCacheLoader implements AsyncCacheLoader<String, ListSpacesResponse> {
		@Override
		public @NonNull CompletableFuture<ListSpacesResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			Mono<ListSpacesResponse> mono = parent.retrieveSpaceIdsInOrg(key)
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class AppsInSpaceCacheLoader implements AsyncCacheLoader<CacheKeyAppsInSpace, ListApplicationsResponse> {
		@Override
		public @NonNull CompletableFuture<ListApplicationsResponse> asyncLoad(
				@NonNull CacheKeyAppsInSpace key, @NonNull Executor executor) {
			Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationIdsInSpace(key.getOrgId(), key.getSpaceId())
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}
	
	private class SpaceSummaryCacheLoader implements AsyncCacheLoader<String, GetSpaceSummaryResponse> {
		@Override
		public @NonNull CompletableFuture<GetSpaceSummaryResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			Mono<GetSpaceSummaryResponse> mono = parent.retrieveSpaceSummary(key)
					.subscribeOn(Schedulers.fromExecutor(executor))
					.cache();
			return mono.toFuture();
		}
	}

	private class DomainCacheLoader implements AsyncCacheLoader<String, ListOrganizationDomainsResponse> {
		@Override
		public @NonNull CompletableFuture<ListOrganizationDomainsResponse> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {
			Mono<ListOrganizationDomainsResponse> mono = parent.retrieveAllDomains(key)
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
		
		this.appsInSpaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new AppsInSpaceCacheLoader());
		this.internalMetrics.addCaffeineCache("appsInSpace", this.appsInSpaceCache);
		
		this.spaceSummaryCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new SpaceSummaryCacheLoader());
		this.internalMetrics.addCaffeineCache("spaceSummary", this.spaceSummaryCache);

		this.domainsInOrgCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheDomainLevelInSeconds, TimeUnit.SECONDS)
				.refreshAfterWrite(this.refreshCacheDomainLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.buildAsync(new DomainCacheLoader());
		this.internalMetrics.addCaffeineCache("domain", this.domainsInOrgCache);
	}

	@Override
	public Mono<GetInfoResponse> getInfo() {
		return this.parent.getInfo();
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
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomains(String orgId) {
		return Mono.fromFuture(this.domainsInOrgCache.get(orgId));
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		// TODO: Implement cache
		return this.parent.retrieveOrgIdV3(orgName);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		// TODO: Implement cache
		return this.parent.retrieveAllOrgIdsV3();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
		// TODO: Implement cache
		return this.parent.retrieveSpaceIdV3(orgId, spaceName);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		// TODO: Implement cache
		return this.parent.retrieveSpaceIdsInOrgV3(orgId);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.applications.ListApplicationsResponse> retrieveAllApplicationIdsInSpaceV3(String orgId, String spaceId) {
		// TODO: Implement cache
		return this.parent.retrieveAllApplicationIdsInSpaceV3(orgId, spaceId);
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
		// TODO: Implement cache
		return this.parent.retrieveSpaceV3(spaceId);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		// TODO: Implement cache
		return this.parent.retrieveAllDomainsV3(orgId);
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId) {
		throw new UnsupportedOperationException();
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

	@Override
	public void invalidateCacheDomain() {
		log.info("Invalidating domain cache");
		this.domainsInOrgCache.synchronous().invalidateAll();		
	}

	@Override
	public void reset() {
		this.parent.reset();
	}
}
