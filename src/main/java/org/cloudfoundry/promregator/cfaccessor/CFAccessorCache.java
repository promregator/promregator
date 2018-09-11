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
	private int timeoutCacheOrgLevel;

	@Value("${cf.cache.timeout.space:3600}")
	private int timeoutCacheSpaceLevel;
	
	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;
	
	private CFAccessor parent;
	
	public CFAccessorCache(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupMaps() {
		this.orgCache = new AutoRefreshingCacheMap<>(Duration.ofSeconds(this.timeoutCacheOrgLevel), Duration.ofSeconds(this.timeoutCacheOrgLevel * 2 / 3), this::orgCacheLoader);
		this.spaceCache = new AutoRefreshingCacheMap<>(Duration.ofSeconds(this.timeoutCacheSpaceLevel), Duration.ofSeconds(this.timeoutCacheSpaceLevel * 2 / 3), this::spaceCacheLoader);
		this.applicationCache = new AutoRefreshingCacheMap<>(Duration.ofSeconds(this.timeoutCacheApplicationLevel), Duration.ofSeconds(this.timeoutCacheApplicationLevel * 2 / 3), this::applicationCacheLoader);
		this.spaceSummaryCache = new AutoRefreshingCacheMap<>(Duration.ofSeconds(this.timeoutCacheApplicationLevel), Duration.ofSeconds(this.timeoutCacheApplicationLevel * 2 / 3), this::spaceSummaryCacheLoader);
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
