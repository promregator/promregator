package org.cloudfoundry.promregator.cfaccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Mono;


public class CFAccessorCache implements CFAccessor {
	private static final Logger log = Logger.getLogger(CFAccessorCache.class);

	/* Cache-related attributes */

	private PassiveExpiringMap<String, Mono<ListOrganizationsResponse>> orgCache;
	private PassiveExpiringMap<String, Mono<ListSpacesResponse>> spaceCache;
	private PassiveExpiringMap<String, Mono<ListApplicationsResponse>> applicationCache;
	private PassiveExpiringMap<String, Mono<GetSpaceSummaryResponse>> spaceSummaryCache;
	
	@Value("${cf.cache.timeout.org:3600}")
	private int timeoutCacheOrgLevel;

	@Value("${cf.cache.timeout.space:3600}")
	private int timeoutCacheSpaceLevel;
	
	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;
	
	@Autowired
	private InternalMetrics internalMetrics;
	
	private CFAccessor parent;
	
	public CFAccessorCache(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupMaps() {
		this.orgCache = new PassiveExpiringMap<>(this.timeoutCacheOrgLevel, TimeUnit.SECONDS);
		this.spaceCache = new PassiveExpiringMap<>(this.timeoutCacheSpaceLevel, TimeUnit.SECONDS);
		/*
		 * NB: There is little point in separating the timeouts between applicationCache
		 * and hostnameMap:
		 * - changes to routes may come easily and thus need to be detected fast
		 * - apps can start and stop, we need to see this, too
		 * - instances can be added to apps
		 * - Blue/green deployment may alter both of them
		 * 
		 * In short: both are very volatile and we need to query them often
		 */
		this.applicationCache = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.spaceSummaryCache = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
	}

	private <P> Mono<P> cacheRetrieval(@NotNull final String retrievalTypeName, @NotNull final String key, @NotNull final PassiveExpiringMap<String, Mono<P>> cacheMap, Function<Void, Mono<P>> requestFunction) {
		// try to fetch result with dirty cache read
		Mono<P> result = cacheMap.get(key);
		if (result != null) {
			this.internalMetrics.countHit("cfaccessor."+retrievalTypeName);
			return result;
		}
		
		// dirty cache read failed; trying synchronized one
		synchronized (key.intern()) {
			result = cacheMap.get(key);
			if (result != null) {
				// result was retrieved in the meantime
				this.internalMetrics.countHit("cfaccessor."+retrievalTypeName);
				return result;
			}
			
			this.internalMetrics.countMiss("cfaccessor."+retrievalTypeName);
			
			result = requestFunction.apply(null);
			// TODO Error handling
			if (result != null) {
				cacheMap.put(key, result);
			}
		}
		
		return result;

	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		
		return this.cacheRetrieval("org", orgName, this.orgCache, nil -> {
			return this.parent.retrieveOrgId(orgName);
		});
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		final String key = String.format("%s|%s", orgId, spaceName);
		
		return this.cacheRetrieval("space", key, this.spaceCache, nil -> {
			return this.parent.retrieveSpaceId(orgId, spaceName);
		});
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName) {
		final String key = this.determineApplicationCacheKey(orgId, spaceId, applicationName);
		
		return this.cacheRetrieval("app", key, this.applicationCache, nil -> {
			return this.parent.retrieveApplicationId(orgId, spaceId, applicationName);
		});
	}

	private String determineApplicationCacheKey(String orgId, String spaceId, String applicationName) {
		return String.format("%s|%s|%s", orgId, spaceId, applicationName);
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
				String appKey = this.determineApplicationCacheKey(orgId, spaceId, appName);
				
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
		return this.cacheRetrieval("spaceSummary", spaceId, this.spaceSummaryCache, nil -> {
			return this.parent.retrieveSpaceSummary(spaceId);
		});
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
