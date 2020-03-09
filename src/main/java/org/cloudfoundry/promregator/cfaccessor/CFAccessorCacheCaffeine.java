package org.cloudfoundry.promregator.cfaccessor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.scanner.BackPressure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class CFAccessorCacheCaffeine implements CFAccessorCache {
	private static final Logger log = Logger.getLogger(CFAccessorCacheCaffeine.class);

	private Cache<String, Mono<ListOrganizationsResponse>> orgCache;
	private Cache<String, Mono<ListOrganizationsResponse>> allOrgIdCache;
	private Cache<CacheKeySpace, Mono<ListSpacesResponse>> spaceCache;
	private Cache<String, Mono<ListSpacesResponse>> spaceIdInOrgCache;
	private Cache<CacheKeyAppsInSpace, Mono<ListApplicationsResponse>> appsInSpaceCache;
	private Cache<String, Mono<GetSpaceSummaryResponse>> spaceSummaryCache;
	
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
				.expireAfterWrite(this.refreshCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.scheduler(caffeineScheduler)
				.recordStats()
				.build();
		this.internalMetrics.addCaffeineCache("orgCache", this.orgCache);
		
		
		this.allOrgIdCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.expireAfterWrite(this.refreshCacheOrgLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.build();
		this.internalMetrics.addCaffeineCache("allOrgCache", this.allOrgIdCache);
		
		this.spaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.expireAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.build();

		this.internalMetrics.addCaffeineCache("spaceCache", this.spaceCache);
		
		this.spaceIdInOrgCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.expireAfterWrite(this.refreshCacheSpaceLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.build();

		this.internalMetrics.addCaffeineCache("spaceInOrgCache", this.spaceIdInOrgCache);
		
		
		this.appsInSpaceCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.expireAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.build();
		this.internalMetrics.addCaffeineCache("appsInSpace", this.appsInSpaceCache);
		
		
		this.spaceSummaryCache = Caffeine.newBuilder()
				.expireAfterAccess(this.expiryCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.expireAfterWrite(this.refreshCacheApplicationLevelInSeconds, TimeUnit.SECONDS)
				.recordStats()
				.scheduler(caffeineScheduler)
				.build();

		this.internalMetrics.addCaffeineCache("spaceSummary", this.spaceSummaryCache);
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		Mono<ListOrganizationsResponse> cachedMono = this.orgCache.getIfPresent(orgName);
		if(cachedMono != null) {
			return cachedMono;
		} else {
			return withBackPressure(() -> {
				Mono<ListOrganizationsResponse> mono = parent.retrieveOrgId(orgName).cache();
				this.orgCache.put(orgName, mono);
				return mono;
			});
		}
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		Mono<ListSpacesResponse> cachedMono = this.spaceCache.getIfPresent(key);
		if(cachedMono != null) {
			return cachedMono;
		} else {
			return withBackPressure(() -> {
				Mono<ListSpacesResponse> mono = parent.retrieveSpaceId(key.getOrgId(), key.getSpaceName()).cache();
				this.spaceCache.put(key, mono);
				return mono;
			});
		}
	}



	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		final CacheKeyAppsInSpace key = new CacheKeyAppsInSpace(orgId, spaceId);
		Mono<ListApplicationsResponse> cachedMono = this.appsInSpaceCache.getIfPresent(key);
		if(cachedMono != null) {
			return cachedMono;
		} else {
			return withBackPressure(() -> {
				Mono<ListApplicationsResponse> mono = parent.retrieveAllApplicationIdsInSpace(key.getOrgId(), key.getSpaceId()).cache();
				this.appsInSpaceCache.put(key, mono);
				return mono;
			});
		}
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		Mono<GetSpaceSummaryResponse> cachedMono = this.spaceSummaryCache.getIfPresent(spaceId);
		if(cachedMono != null) {
			return cachedMono;
		} else {
			return withBackPressure(() -> {
				Mono<GetSpaceSummaryResponse> mono = parent.retrieveSpaceSummary(spaceId).cache();
				this.spaceSummaryCache.put(spaceId, mono);
				return mono;
			});
		}
	}

	
	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		Mono<ListOrganizationsResponse> cachedMono = this.allOrgIdCache.getIfPresent("all");
		if(cachedMono != null) {
			return cachedMono;
		} else {
			return withBackPressure(() -> {
				Mono<ListOrganizationsResponse> mono = parent.retrieveAllOrgIds().cache();
				this.allOrgIdCache.put("all", mono);
				return mono;
			});
		}
	}
	
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		Mono<ListSpacesResponse> cachedMono = this.spaceIdInOrgCache.getIfPresent(orgId);
		if(cachedMono != null) {
			return cachedMono;
		} else {
			return withBackPressure(() -> {
				Mono<ListSpacesResponse> mono = parent.retrieveSpaceIdsInOrg(orgId).cache();
				this.spaceIdInOrgCache.put(orgId, mono);
				return mono;
			});
		}
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
		this.spaceIdInOrgCache.invalidateAll();
	}

	@Override
	public void invalidateCacheOrg() {
		log.info("Invalidating org cache");
		this.orgCache.invalidateAll();
		this.allOrgIdCache.invalidateAll();
	}

	private <T> Mono<T> withBackPressure(Supplier<Mono<T>> originalMono){
		return Mono.subscriberContext().flatMap(ctx -> {
			Optional<BackPressure> bpOpt = ctx.getOrEmpty("backpressure");
			if(bpOpt.isPresent()) {
				BackPressure bp = bpOpt.get();
				log.info("Duration"+bp.runDuration().toMillis());
				return bp.request(originalMono);
			} else {
				return originalMono.get();
			}
		});
	}
}
