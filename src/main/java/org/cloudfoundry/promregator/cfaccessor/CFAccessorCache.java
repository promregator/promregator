package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.cache.AutoRefreshingCacheMap;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Mono;

public class CFAccessorCache implements CFAccessor {
	private static final Logger log = Logger.getLogger(CFAccessorCache.class);

	private AutoRefreshingCacheMap<String, Mono<ListOrganizationsResponse>> orgCache;
	private AutoRefreshingCacheMap<CacheKeySpace, Mono<ListSpacesResponse>> spaceCache;
	private AutoRefreshingCacheMap<CacheKeyApplication, Mono<ListApplicationsResponse>> applicationCache;
	private AutoRefreshingCacheMap<String, Mono<GetSpaceSummaryResponse>> spaceSummaryCache;
	
	@Value("${cf.cache.timeout.org:3600}")
	private int timeoutCacheOrgLevelInSeconds;

	@Value("${cf.cache.timeout.space:3600}")
	private int timeoutCacheSpaceLevelInSeconds;
	
	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevelInSeconds;
	
	@Value("${cf.request.timeout.org:2500}") // Warning! Default value defined multiple times in the source code!
	private int requestTimeoutOrgInMilliseconds;

	@Value("${cf.request.timeout.space:2500}") // Warning! Default value defined multiple times in the source code!
	private int requestTimeoutSpaceInMilliseconds;

	@Value("${cf.request.timeout.app:2500}") // Warning! Default value defined multiple times in the source code!
	private int requestTimeoutApplicationInMilliseconds;
	
	@Value("${cf.request.timeout.appInSpace:2500}") // Warning! Default value defined multiple times in the source code!
	private int requestTimeoutAppInSpaceInMilliseconds;
	
	@Value("${cf.request.timeout.appSummary:4000}") // Warning! Default value defined multiple times in the source code!
	private int requestTimeoutAppSummaryInMilliseconds;

	
	private CFAccessor parent;
	
	public CFAccessorCache(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupMaps() {
		/* 
		 * calculate refresh intervals
		 */
		
		long refreshOrgInMilliseconds = this.timeoutCacheOrgLevelInSeconds * 1000 - 3 * this.requestTimeoutOrgInMilliseconds;
		if (refreshOrgInMilliseconds < 0) {
			log.warn("The request timeout for org requests is too long for your cache. Falling back to default values.");
			refreshOrgInMilliseconds = 3600 * 1000 - 3 * 2500;
		}
		
		long refreshSpaceInMilliseconds = this.timeoutCacheSpaceLevelInSeconds * 1000 - 3 * this.requestTimeoutSpaceInMilliseconds;
		if (refreshSpaceInMilliseconds < 0) {
			log.warn("The request timeout for space requests is too long for your cache. Falling back to default values.");
			refreshSpaceInMilliseconds = 3600 * 1000 - 3 * 2500;
		}
		
		long refreshAppInMilliseconds = this.timeoutCacheApplicationLevelInSeconds * 1000 - 3 * Math.max(this.requestTimeoutApplicationInMilliseconds,  this.requestTimeoutAppInSpaceInMilliseconds);
		if (refreshAppInMilliseconds < 0) {
			log.warn("The request timeout for app requests is too long for your cache. Falling back to default values.");
			refreshAppInMilliseconds = 300 * 1000 - 3 * 2500;
		}
		
		long refreshAppSummaryInMilliseconds = this.timeoutCacheApplicationLevelInSeconds * 1000 - 3 * this.requestTimeoutAppSummaryInMilliseconds;
		if (refreshAppSummaryInMilliseconds < 0) {
			log.warn("The request timeout for app summary requests is too long for your cache. Falling back to default values.");
			refreshAppSummaryInMilliseconds = 300 * 1000 - 3 * 4000;
		}
		
		log.info(String.format("Cache refresh timings: org cache: %dms, space cache: %dms, app cache: %dms, app summary cache: %dms", 
				refreshOrgInMilliseconds, refreshSpaceInMilliseconds, refreshAppInMilliseconds, refreshAppSummaryInMilliseconds));
		
		/*
		 * initializing caches
		 */
		this.orgCache = new AutoRefreshingCacheMap<>("org", Duration.ofSeconds(this.timeoutCacheOrgLevelInSeconds), Duration.ofMillis(refreshOrgInMilliseconds), this::orgCacheLoader);
		this.spaceCache = new AutoRefreshingCacheMap<>("space", Duration.ofSeconds(this.timeoutCacheSpaceLevelInSeconds), Duration.ofMillis(refreshSpaceInMilliseconds), this::spaceCacheLoader);
		this.applicationCache = new AutoRefreshingCacheMap<>("application", Duration.ofSeconds(this.timeoutCacheApplicationLevelInSeconds), Duration.ofMillis(refreshAppInMilliseconds), this::applicationCacheLoader);
		this.spaceSummaryCache = new AutoRefreshingCacheMap<>("spaceSummary", Duration.ofSeconds(this.timeoutCacheApplicationLevelInSeconds), Duration.ofMillis(refreshAppSummaryInMilliseconds), this::spaceSummaryCacheLoader);
	}

	private Mono<ListOrganizationsResponse> orgCacheLoader(String orgName) {
		return this.parent.retrieveOrgId(orgName);
	}
	
	private Mono<ListSpacesResponse> spaceCacheLoader(CacheKeySpace cacheKey) {
		return this.parent.retrieveSpaceId(cacheKey.getOrgId(), cacheKey.getSpaceName());
	}
	
	private Mono<ListApplicationsResponse> applicationCacheLoader(CacheKeyApplication cacheKey) {
		return this.parent.retrieveApplicationId(cacheKey.getOrgId(), cacheKey.getSpaceId(), cacheKey.getApplicationName());
	}
	
	private Mono<GetSpaceSummaryResponse> spaceSummaryCacheLoader(String spaceId) {
		return this.parent.retrieveSpaceSummary(spaceId);
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		return this.orgCache.get(orgName);
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		
		return this.spaceCache.get(key);
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName) {
		final CacheKeyApplication key = new CacheKeyApplication(orgId, spaceId, applicationName);
		
		return this.applicationCache.get(key);
	}
	
	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		/*
		 * special case: we don't cache the result here in an own cache,
		 * as we always want to have "fresh data".
		 * However, the result of the request we send is helpful for another cache, 
		 * so we fill that one, too (if we anyhow have already retrieved the data for it).
		 */
		
		Mono<ListApplicationsResponse> result = this.parent.retrieveAllApplicationIdsInSpace(orgId, spaceId);
		
		Mono<ListApplicationsResponse> resultProcessed = result.doOnEach(signal -> {
			if (!signal.isOnNext()) {
				return;
			}
			
			// preload the cache with the responses we got
			List<ApplicationResource> appResources = signal.get().getResources();
			for(ApplicationResource ar : appResources) {
				String appName = ar.getEntity().getName();
				CacheKeyApplication appKey = new CacheKeyApplication(orgId, spaceId, appName);
				
				List<ApplicationResource> arList = new ArrayList<>();
				arList.add(ar);
				
				ListApplicationsResponse cacheValue = ListApplicationsResponse.builder().addAllResources(arList).build();
				this.applicationCache.putIfAbsent(appKey, Mono.just(cacheValue));
			}
		});
		
		return resultProcessed;
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		return this.spaceSummaryCache.get(spaceId);
	}

	public void invalidateCacheApplications() {
		log.info("Invalidating application cache");
		this.applicationCache.clear();
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
