package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.GetApplicationRequest;
import org.cloudfoundry.client.v2.applications.GetApplicationResponse;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoRequest;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.GetOrganizationRequest;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceRequest;
import org.cloudfoundry.client.v2.spaces.GetSpaceResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryRequest;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstanceServiceBindingsRequest;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstancesRequest;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstancesResponse;
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

import reactor.core.Exceptions;
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
	
	@Value("${cf.request.timeout.org:2500}")
	private int requestTimeoutOrg;

	@Value("${cf.request.timeout.space:2500}")
	private int requestTimeoutSpace;

	@Value("${cf.request.timeout.app:2500}")
	private int requestTimeoutApplication;
	
	@Value("${cf.request.timeout.appInSpace:2500}")
	private int requestTimeoutAppInSpace;
	
	@Value("${cf.request.timeout.appSummary:4000}")
	private int requestTimeoutAppSummary;

	@Value("${promregator.internal.preCheckAPIVersion:true}")
	private boolean performPrecheckOfAPIVersion;
	
	@Autowired
	private InternalMetrics internalMetrics;

	
	private static final Pattern PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
	
	private ReactorCloudFoundryClient cloudFoundryClient;

	
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
		
		if (this.performPrecheckOfAPIVersion) {
			GetInfoRequest request = GetInfoRequest.builder().build();
			GetInfoResponse getInfo = this.cloudFoundryClient.info().get(request).block();
			// NB: This also ensures that the connection has been established properly...
			log.info(String.format("Target CF platform is running on API version %s", getInfo.getApiVersion()));
		}
	}

	/**
	 * performs standard, cached retrieval from the CF Cloud Controller
	 * @param retrievalTypeName the name of type of the request which is being made; used for identification in internalMetrics
	 * @param logName the name of the logger category, which shall be used for logging this Reactor operation
	 * @param key the key for which the request is being made (e.g. orgId, orgId|spaceName, ...)
	 * @param cacheMap the PassiveExpiringMap, which shall be used for caching the request; may be <code>null</code> to indicate that no caching shall take place
	 * @param requestData an object which is being used as input parameter for the request
	 * @param requestFunction a function which calls the CF API operation, which is being made, <code>requestData</code> is used as input parameter for this function.
	 * @return a Mono on the response provided by the CF Cloud Controller
	 */
	private <P, R> Mono<P> performGenericRetrieval(String retrievalTypeName, String logName, String key, R requestData, Function<R, Mono<P>> requestFunction, int timeoutInMS) {
		synchronized(key.intern()) {
			Mono<P> result = null;

			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, retrievalTypeName);
			
			result = Mono.just(requestData)
				// start the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().start();
					return tuple.getT1();
				})
				.flatMap( requestFunction )
				.timeout(Duration.ofMillis(timeoutInMS))
				.retry(2)
				.doOnError(throwable -> {
					Throwable unwrappedThrowable = Exceptions.unwrap(throwable);
					if (unwrappedThrowable instanceof TimeoutException) {
						log.error(String.format("Async retrieval of %s with key %s caused a timeout after %d even though we tried three times", logName, key, timeoutInMS));
					} else {
						log.error(String.format("Async retrieval of %s with key %s raised a reactor error", logName, key), unwrappedThrowable);
					}
				})
				// stop the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().stop();
					return tuple.getT1();
				})
				.log(log.getName()+"."+logName, Level.FINE)
				.cache();
			
			return result;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveOrgId(java.lang.String)
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
		
		return this.performGenericRetrieval("org", "retrieveOrgId", orgName, orgsRequest, or -> {
			return this.cloudFoundryClient.organizations().list(or);
		}, this.requestTimeoutOrg);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllOrgIds()
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().build();
		
		return this.performGenericRetrieval("allOrgs", "retrieveAllOrgIds", "(empty)", orgsRequest, or -> {
			return this.cloudFoundryClient.organizations().list(or);
		}, this.requestTimeoutOrg);
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceId(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		String key = String.format("%s|%s", orgId, spaceName);
		
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
		
		return this.performGenericRetrieval("space", "retrieveSpaceId", key, spacesRequest, sr -> {
			return this.cloudFoundryClient.spaces().list(sr);
		}, this.requestTimeoutSpace);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceIdsInOrg(java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).build();
		
		return this.performGenericRetrieval("space", "retrieveAllSpaceIdsInOrg", orgId, spacesRequest, sr -> {
			return this.cloudFoundryClient.spaces().list(sr);
		}, this.requestTimeoutSpace);
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
		
		return this.performGenericRetrieval("allApps", "retrieveAllApplicationIdsInSpace", key, 
				request, r -> this.cloudFoundryClient.applicationsV2().list(r), this.requestTimeoutAppInSpace);
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		GetSpaceSummaryRequest request = GetSpaceSummaryRequest.builder().spaceId(spaceId).build();
		
		return this.performGenericRetrieval("spaceSummary", "retrieveSpaceSummary", spaceId, 
				request, r -> this.cloudFoundryClient.spaces().getSummary(r), this.requestTimeoutAppSummary);

	}

	@Override
	public Mono<ListUserProvidedServiceInstancesResponse> retrieveAllUserProvidedService() {
		ListUserProvidedServiceInstancesRequest request = ListUserProvidedServiceInstancesRequest.builder().build();
		
		Mono<ListUserProvidedServiceInstancesResponse> response = this.performGenericRetrieval("ups", "retrieveAllUserProvidedServices", "(empty)", request, 
				r -> this.cloudFoundryClient.userProvidedServiceInstances().list(r), 7000); // TODO make it customizable
		
		return response;
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpace(String spaceId) {
		GetSpaceRequest request = GetSpaceRequest.builder().spaceId(spaceId).build();
		return this.performGenericRetrieval("spaceSingle", "retrieveSpace", spaceId, request, 
				r -> this.cloudFoundryClient.spaces().get(r), this.requestTimeoutSpace);
	}

	@Override
	public Mono<GetOrganizationResponse> retrieveOrg(String orgId) {
		GetOrganizationRequest request = GetOrganizationRequest.builder().organizationId(orgId).build();
		return this.performGenericRetrieval("orgSingle", "retrieveOrg", orgId, request, 
				r -> this.cloudFoundryClient.organizations().get(r), this.requestTimeoutOrg);
	}

	@Override
	public Mono<ListUserProvidedServiceInstanceServiceBindingsResponse> retrieveUserProvidedServiceBindings(
			String upsId) {
		ListUserProvidedServiceInstanceServiceBindingsRequest request = ListUserProvidedServiceInstanceServiceBindingsRequest.builder()
				.userProvidedServiceInstanceId(upsId).build();
		
		return this.performGenericRetrieval("upsBindingSingle", "retrieveUserProvidedServiceBindings", upsId, request, 
				r -> this.cloudFoundryClient.userProvidedServiceInstances().listServiceBindings(r), 2500); // TODO make it customizable
	}

}
