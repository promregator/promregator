package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.events.ListEventsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.cache.AutoRefreshingCacheMap;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;

import reactor.core.publisher.Mono;

public class CFAccessorCache implements CFAccessor {
	private static final Logger log = Logger.getLogger(CFAccessorCache.class);

	private AutoRefreshingCacheMap<String, Mono<ListOrganizationsResponse>> orgCache;
	private AutoRefreshingCacheMap<CacheKeySpace, Mono<ListSpacesResponse>> spaceCache;
	private AutoRefreshingCacheMap<String, Mono<GetSpaceSummaryResponse>> spaceSummaryCache;
	
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
	
	public CFAccessorCache(CFAccessor parent) {
		this.parent = parent;
	}
	
	@PostConstruct
	public void setupMaps() {
		log.info(String.format("Cache refresh timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds", 
				this.refreshCacheOrgLevelInSeconds, this.refreshCacheSpaceLevelInSeconds, this.refreshCacheApplicationLevelInSeconds, this.refreshCacheApplicationLevelInSeconds));
		log.info(String.format("Cache expiry timings: org cache: %ds, space cache: %ds, app cache: %ds, app summary cache: %ds", 
				this.expiryCacheOrgLevelInSeconds, this.expiryCacheSpaceLevelInSeconds, this.expiryCacheApplicationLevelInSeconds, this.expiryCacheApplicationLevelInSeconds));
		
		/*
		 * initializing caches
		 */
		this.orgCache = new AutoRefreshingCacheMap<>("org", this.internalMetrics, Duration.ofSeconds(this.expiryCacheOrgLevelInSeconds), Duration.ofSeconds(this.refreshCacheOrgLevelInSeconds), this::orgCacheLoader);
		this.spaceCache = new AutoRefreshingCacheMap<>("space", this.internalMetrics, Duration.ofSeconds(this.expiryCacheSpaceLevelInSeconds), Duration.ofSeconds(refreshCacheSpaceLevelInSeconds), this::spaceCacheLoader);
		this.spaceSummaryCache = new AutoRefreshingCacheMap<>("spaceSummary", this.internalMetrics, Duration.ofSeconds(this.expiryCacheApplicationLevelInSeconds), Duration.ofSeconds(refreshCacheApplicationLevelInSeconds), this::spaceSummaryCacheLoader);
	}

	private Mono<ListOrganizationsResponse> orgCacheLoader(String orgName) {
		Mono<ListOrganizationsResponse> mono = this.parent.retrieveOrgId(orgName);
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}
	
	private Mono<ListSpacesResponse> spaceCacheLoader(CacheKeySpace cacheKey) {
		Mono<ListSpacesResponse> mono = this.parent.retrieveSpaceId(cacheKey.getOrgId(), cacheKey.getSpaceName());
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}
	
	private Mono<GetSpaceSummaryResponse> spaceSummaryCacheLoader(String spaceId) {
		Mono<GetSpaceSummaryResponse> mono = this.parent.retrieveSpaceSummary(spaceId);
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		Mono<ListOrganizationsResponse> mono = this.orgCache.get(orgName);

		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		final CacheKeySpace key = new CacheKeySpace(orgId, spaceName);
		
		Mono<ListSpacesResponse> mono = this.spaceCache.get(key);
		
		/*
		 * Note that the mono does not have any subscriber, yet! 
		 * The cache which we are using is working "on-stock", i.e. we need to ensure
		 * that the underlying calls to the CF API really is triggered.
		 * Fortunately, we can do this very easily:
		 */
		mono.subscribe();
		return mono;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		/*
		 * special case: we don't cache the result here in an own cache,
		 * as we always want to have "fresh data".
		 */
		Mono<ListApplicationsResponse> result = this.parent.retrieveAllApplicationIdsInSpace(orgId, spaceId);
		
		return result;
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

	/**
	 * @param sinceTimestamp
	 * @return
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveEvents(java.lang.String)
	 */
	@Override
	public Mono<ListEventsResponse> retrieveEvents(Instant sinceTimestamp) {
		/*
		 * special case: we don't cache the result here in an own cache,
		 * as we always want to have "fresh data".
		 */
		return parent.retrieveEvents(sinceTimestamp);
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		return this.spaceSummaryCache.get(spaceId);
	}

	public void invalidateCacheApplications() {
		log.info("Invalidating application cache");
		this.spaceSummaryCache.clear();
	}
	
	public void invalidateCacheSpace() {
		log.info("Invalidating space cache");
		this.spaceCache.clear();
	}

	public void invalidateCacheOrg() {
		log.info("Invalidating org cache");
		this.orgCache.clear();
	}
	
	private void deleteSingleOrgFromSpaceCache(String orgId) {
		List<CacheKeySpace> toBeDeleted = new LinkedList<>();
		for (Entry<CacheKeySpace, Mono<ListSpacesResponse>> entry : this.spaceCache.entrySet()) {
			if (entry.getKey().getOrgId().equals(orgId)) {
				toBeDeleted.add(entry.getKey());
			}
		}
		for (CacheKeySpace key : toBeDeleted) {
			this.spaceCache.remove(key);
		}
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_APP_CHANGED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForChangeApp(String spaceId) {
		log.info(String.format("SpaceSummary cache invalidated for space id '%s' due to CF event on application", spaceId));
		this.spaceSummaryCache.remove(spaceId);
	}

	@JmsListener(destination=MessageBusDestination.CF_EVENT_APP_CREATED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForCreateApp(String spaceId) {
		log.info(String.format("SpaceSummary cache invalidated for space id '%s' due to CF event on creation of an application", spaceId));
		this.spaceSummaryCache.remove(spaceId);
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_APP_DELETED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForDeleteApp(String spaceId) {
		log.info(String.format("SpaceSummary cache invalidated for space id '%s' due to CF event on deletion of an application", spaceId));
		this.spaceSummaryCache.remove(spaceId);
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_ROUTE_TO_APP_CHANGED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForAppDueToRouteChange(String spaceId) {
		log.info(String.format("SpaceSummary cache invalidated for space id '%s' due to CF event on route change", spaceId));
		this.spaceSummaryCache.remove(spaceId);
	}
	
	// Note that we do not have to react on a CF_EVENT_SPACE_CREATED event: the cache is not made inconsistent by this
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_SPACE_CHANGED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheSpaceChanged(String orgId) {
		log.info(String.format("Space cache invalidated for all spaces in org '%s' due to CF event on space changed", orgId));
		
		/*
		 * Note: From the event we cannot know the old spaceName. Thus, we cannot derive a CacheKeySpace
		 * for it - and thus we cannot find the exact space to invalidate in the cache. 
		 * The best thing we can do is to invalidate all spaces for that single org only.
		 */
		
		deleteSingleOrgFromSpaceCache(orgId);
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_SPACE_DELETED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheSpaceDeleted(CacheKeySpace key) {
		log.info(String.format("Space cache invalidated for space name '%s' in org '%s' due to CF event on space changed", key.getSpaceName(), key.getOrgId()));
		this.spaceCache.remove(key);
		
		/* 
		 * Note: it would be good also to clean up the spaceSummaryCache here. However, this is not necessary, as the automated
		 * cache cleanup functionality will do this automatically (no-one will be able to access the spaceSummaries there anyway anymore).
		 */
		
	}
	
	// Note that we do not have to react on a CF_EVENT_ORG_CREATED event: the cache is not made inconsistent by this
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_ORG_DELETED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheOrgDeleted(String orgId) {
		/* We do not have the orgId available here.
		 * However, that event is considered to be so seldom for Promregator,
		 * that we accept that we invalidate the entire org cache
		 */
		log.info(String.format("Org cache completely invalidated due to CF event on org '%s' was deleted", orgId));
		this.invalidateCacheOrg();
		
		log.info(String.format("Space cache invalidated for all spaces in org '%s' due to CF event on org was deleted", orgId));
		deleteSingleOrgFromSpaceCache(orgId);
		
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_ORG_CHANGED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheOrgChanged() {
		log.info(String.format("Org cache completely invalidated due to CF event on org changed"));
		
		/*
		 * Note: From the event we do not know the old orgName. 
		 * Thus, we cannot find the exact org to invalidate in the cache. 
		 * The best thing we can do is to invalidate all orgs.
		 */
		this.invalidateCacheOrg();
	}
}
