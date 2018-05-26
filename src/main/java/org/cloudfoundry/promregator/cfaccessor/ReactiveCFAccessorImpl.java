package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Null;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsRequest;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsResponse;
import org.cloudfoundry.client.v2.routes.GetRouteRequest;
import org.cloudfoundry.client.v2.routes.GetRouteResponse;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainRequest;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesRequest;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext.Builder;
import org.cloudfoundry.reactor.ProxyConfiguration;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class ReactiveCFAccessorImpl implements CFAccessor {
	private static final Logger log = Logger.getLogger(ReactiveCFAccessorImpl.class);
	
	@Value("${cf.api_host}")
	private String apiHost;

	@Value("${cf.username}")
	private String username;
	
	@Value("${cf.password}")
	private String password;
	
	@Value("${cf.skipSslValidation:false}")
	private boolean skipSSLValidation;
	
	@Value("${cf.proxyHost:#{null}}") 
	private String proxyHost;
	
	@Value("${cf.proxyPort:0}") 
	private int proxyPort;
	
	/* Cache-related attributes */

	private PassiveExpiringMap<String, Mono<ListOrganizationsResponse>> orgCache;
	private PassiveExpiringMap<String, Mono<ListSpacesResponse>> spaceCache;
	private PassiveExpiringMap<String, Mono<ListApplicationsResponse>> applicationCache;
	private PassiveExpiringMap<String, Mono<ListRouteMappingsResponse>> routeMappingCache;
	private PassiveExpiringMap<String, Mono<GetRouteResponse>> routeCache;
	private PassiveExpiringMap<String, Mono<GetSharedDomainResponse>> domainCache;
	private PassiveExpiringMap<String, Mono<ListProcessesResponse>> processCache;
	
	@Value("${cf.cache.timeout.org:3600}")
	private int timeoutCacheOrgLevel;

	@Value("${cf.cache.timeout.space:3600}")
	private int timeoutCacheSpaceLevel;
	
	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;
	
	@Autowired
	private InternalMetrics internalMetrics;

	
	private static final Pattern PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://");
	
	private ReactorCloudFoundryClient cloudFoundryClient;

	
	
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
		this.routeMappingCache = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.routeCache = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.domainCache = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.processCache = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
	}
	
	private DefaultConnectionContext connectionContext(ProxyConfiguration proxyConfiguration) throws ConfigurationException {
		if (apiHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(apiHost).find()) {
			throw new ConfigurationException("cf.api_host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}

		Builder connctx = DefaultConnectionContext.builder().apiHost(apiHost).skipSslValidation(skipSSLValidation);
		
		if (proxyConfiguration != null) {
			connctx = connctx.proxyConfiguration(proxyConfiguration);
		}
		return connctx.build();
	}

	private PasswordGrantTokenProvider tokenProvider() {
		return PasswordGrantTokenProvider.builder().password(this.password).username(this.username).build();
	}

	private ProxyConfiguration proxyConfiguration() throws ConfigurationException {
		if (this.proxyHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(this.proxyHost).find()) {
			throw new ConfigurationException("cf.proxyHost configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}
		
		if (this.proxyHost != null && this.proxyPort != 0) {
			
			String proxyIP = null;
			if (!InetAddressUtils.isIPv4Address(this.proxyHost) && !InetAddressUtils.isIPv6Address(this.proxyHost)) {
				/*
				 * NB: There is currently a bug in io.netty.util.internal.SocketUtils.connect()
				 * which is called implicitly by the CF API Client library, which leads to the effect
				 * that a hostname for the proxy isn't resolved. Thus, it is only possible to pass 
				 * IP addresses as proxy names.
				 * To work around this issue, we manually perform a resolution of the hostname here
				 * and then feed that one to the CF API Client library...
				 */
				try {
					InetAddress ia = InetAddress.getByName(this.proxyHost);
					proxyIP = ia.getHostAddress();
				} catch (UnknownHostException e) {
					throw new ConfigurationException("The cf.proxyHost provided cannot be resolved to an IP address; is there a typo in your configuration?", e);
				}
			} else {
				// the address specified is already an IP address
				proxyIP = this.proxyHost;
			}
			
			return ProxyConfiguration.builder().host(proxyIP).port(this.proxyPort).build();
			
		} else {
			return null;
		}
	}
	
	private ReactorCloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorCloudFoundryClient.builder().connectionContext(connectionContext).tokenProvider(tokenProvider).build();
	}
	
	@PostConstruct
	@SuppressWarnings("PMD.UnusedPrivateMethod") // method is really required
	private void constructCloudFoundryClient() throws ConfigurationException {
		ProxyConfiguration proxyConfiguration = this.proxyConfiguration();
		DefaultConnectionContext connectionContext = this.connectionContext(proxyConfiguration);
		PasswordGrantTokenProvider tokenProvider = this.tokenProvider();
		
		this.cloudFoundryClient = this.cloudFoundryClient(connectionContext, tokenProvider);
	}

	/**
	 * performs standard, cached retrieval from the CF API server
	 * @param retrievalTypeName the name of type of the request which is being made; used for identification in internalMetrics
	 * @param logName the name of the logger category, which shall be used for logging this Reactor operation
	 * @param key the key for which the request is being made (e.g. orgId, orgId|spaceName, ...)
	 * @param cacheMap the PassiveExpiringMap, which shall be used for caching the request; may be <code>null</code> to indicate that no caching shall take place
	 * @param requestData an object which is being used as input parameter for the request
	 * @param requestFunction a function which calls the CF API operation, which is being made, <code>requestData</code> is used as input parameter for this function.
	 * @return
	 */
	private <P, R> Mono<P> performGenericRetrieval(String retrievalTypeName, String logName, String key, @Null PassiveExpiringMap<String, Mono<P>> cacheMap, R requestData, Function<R, Mono<P>> requestFunction) {
		synchronized(key.intern()) {
			Mono<P> result = null;
			
			if (cacheMap != null) {
				// caching takes place at all
				result = cacheMap.get(key);
				if (result != null) {
					this.internalMetrics.countHit("cfaccessor."+retrievalTypeName);
					return result;
				}
				
				this.internalMetrics.countMiss("cfaccessor."+retrievalTypeName);
			}

			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, retrievalTypeName);
			
			result = Mono.just(requestData)
				// start the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().start();
					return tuple.getT1();
				})
				.flatMap( requestFunction )
				.retry(2)
				.doOnError(throwable -> {
					log.error(String.format("Retrieval of %s with key %s raised a reactor error", logName, key), throwable);
				})
				// stop the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().stop();
					return tuple.getT1();
				})
				.log(log.getName()+"."+logName, Level.FINE)
				.cache();

			if (cacheMap != null) {
				cacheMap.put(key, result);
			}
			
			return result;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveOrgId(java.lang.String)
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
		
		return this.performGenericRetrieval("org", "retrieveOrgId", orgName, this.orgCache, orgsRequest, or -> {
			return this.cloudFoundryClient.organizations().list(or);
		});
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceId(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		String key = String.format("%s|%s", orgId, spaceName);
		
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
		
		return this.performGenericRetrieval("space", "retrieveSpaceId", key, this.spaceCache, spacesRequest, sr -> {
			return this.cloudFoundryClient.spaces().list(sr);
		});
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveApplicationId(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName) {
		String key = String.format("%s|%s|%s", orgId, spaceId, applicationName);
		
		ListApplicationsRequest request = ListApplicationsRequest.builder()
			.organizationId(orgId)
			.spaceId(spaceId)
			.name(applicationName)
			.build();
		
		return this.performGenericRetrieval("app", "retrieveApplicationId", key, this.applicationCache, 
			request, r ->  this.cloudFoundryClient.applicationsV2().list(r));
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllApplicationIdsInSpace(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		String key = String.format("%s|%s", orgId, spaceId);
		ListApplicationsRequest request = ListApplicationsRequest.builder()
				.organizationId(orgId)
				.spaceId(spaceId)
				.build();
		
		return this.performGenericRetrieval("allApps", "retrieveAllApplicationIdsInSpace", key, null, 
				request, r -> this.cloudFoundryClient.applicationsV2().list(r));
	}

	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRouteMapping(java.lang.String)
	 */
	@Override
	public Mono<ListRouteMappingsResponse> retrieveRouteMapping(String appId) {
		ListRouteMappingsRequest mappingRequest = ListRouteMappingsRequest.builder().applicationId(appId).build();
		
		return this.performGenericRetrieval("routeMapping", "retrieveRouteMapping", appId, this.routeMappingCache, 
				mappingRequest, r ->  this.cloudFoundryClient.routeMappings().list(r));
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRoute(java.lang.String)
	 */
	@Override
	public Mono<GetRouteResponse> retrieveRoute(String routeId) {
		GetRouteRequest getRequest = GetRouteRequest.builder().routeId(routeId).build();
		
		return this.performGenericRetrieval("route", "retrieveRoute", routeId, this.routeCache, 
				getRequest, r -> this.cloudFoundryClient.routes().get(r));
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSharedDomain(java.lang.String)
	 */
	@Override
	public Mono<GetSharedDomainResponse> retrieveSharedDomain(String domainId) {
		GetSharedDomainRequest domainRequest = GetSharedDomainRequest.builder().sharedDomainId(domainId).build();
		
		return this.performGenericRetrieval("domain", "retrieveSharedDomain", domainId, this.domainCache, 
				domainRequest, r -> this.cloudFoundryClient.sharedDomains().get(r));
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveProcesses(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListProcessesResponse> retrieveProcesses(String orgId, String spaceId, String appId) {
		String key = String.format("%s|%s|%s", orgId, spaceId, appId);
		
		ListProcessesRequest request = ListProcessesRequest.builder().organizationId(orgId).spaceId(spaceId).applicationId(appId).build();
		
		return this.performGenericRetrieval("processes", "retrieveProcesses", key, this.processCache, 
				request, r -> this.cloudFoundryClient.processes().list(r));
	}

	
	public void invalidateCacheApplications() {
		log.info("Invalidating application cache");
		this.applicationCache.clear();
		this.routeMappingCache.clear();
		this.routeCache.clear();
		this.domainCache.clear();
		this.processCache.clear();
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
