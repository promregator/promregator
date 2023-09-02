package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.logging.log4j.util.Strings;
import org.cloudfoundry.client.v3.Pagination;
import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesRequest;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.ListRoutesRequest;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.promregator.cfaccessor.client.InfoV3;
import org.cloudfoundry.promregator.cfaccessor.client.ReactorInfoV3;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext.Builder;
import org.cloudfoundry.reactor.ProxyConfiguration;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Mono;

public class ReactiveCFAccessorImpl implements CFAccessor {

	private static final int MAXIMAL_NUMBER_OF_ROUTES_PER_CAPI_REQUEST = 5000;
	
	private static final Logger log = LoggerFactory.getLogger(ReactiveCFAccessorImpl.class);
	public static final ListApplicationsResponse INVALID_APPLICATIONS_RESPONSE = ListApplicationsResponse.builder().build();
	
	private static final String CF_API_V3_PROCESS_TYPE_WEB = "web";

	@Value("${cf.api_host}")
	private String apiHost;

	@Value("${cf.username}")
	private String username;
	
	@Value("${cf.password}")
	private String password;
	
	@Value("${cf.skipSslValidation:false}")
	private boolean skipSSLValidation;

	/**
	 * The hostname of the HTTP proxy.
	 */
	@Value("${cf.proxy.host:#{null}}") 
	private String proxyHost;
	
	/**
	 * The port of the HTTP proxy.
	 */
	@Value("${cf.proxy.port:0}") 
	private int proxyPort;
	
	@Value("${cf.request.timeout.org:2500}")
	private int requestTimeoutOrg;

	@Value("${cf.request.timeout.space:2500}")
	private int requestTimeoutSpace;

	@Value("${cf.request.timeout.appInSpace:2500}")
	private int requestTimeoutAppInSpace;
	
	@Value("${cf.request.timeout.domain:2500}")
	private int requestTimeoutDomains;

	@Value("${cf.request.timeout.route:2500}")
	private int requestTimeoutRoute;
	
	@Value("${cf.request.timeout.process:2500}")
	private int requestTimeoutProcess;
	
	@Value("${cf.connectionPool.size:#{null}}")
	private Integer connectionPoolSize;
	
	@Value("${cf.threadPool.size:#{null}}")
	private Integer threadPoolSize;

	@Value("${promregator.internal.preCheckAPIVersion:true}")
	private boolean performPrecheckOfAPIVersion;
	
	@Value("${cf.request.rateLimit:0}") 
	private double requestRateLimit;
	
	@Value("${cf.request.backoff:500}") 
	private long backoffDelay;
	
	@Autowired
	private InternalMetrics internalMetrics;

	
	private static final Pattern PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
	
	private ReactorCloudFoundryClient cloudFoundryClient;
	private ReactiveCFPaginatedRequestFetcher paginatedRequestFetcher;
	
	private DefaultConnectionContext connectionContext(ProxyConfiguration proxyConfiguration) throws ConfigurationException {
		if (apiHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(apiHost).find()) {
			throw new ConfigurationException("cf.api_host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}

		Builder connctx = DefaultConnectionContext.builder()
				.apiHost(apiHost)
				.skipSslValidation(skipSSLValidation);
		
		if (proxyConfiguration != null) {
			connctx = connctx.proxyConfiguration(proxyConfiguration);
		}
		
		if (this.connectionPoolSize != null) {
			connctx = connctx.connectionPoolSize(this.connectionPoolSize);
		}
		
		if (this.threadPoolSize != null) {
			connctx = connctx.threadPoolSize(this.threadPoolSize);
		}
		
		return connctx.build();
	}

	private PasswordGrantTokenProvider tokenProvider() {
		return PasswordGrantTokenProvider.builder().password(this.password).username(this.username).build();
	}

	private ProxyConfiguration proxyConfiguration() throws ConfigurationException {
		
		String effectiveProxyHost = this.proxyHost;
		int effectiveProxyPort = this.proxyPort;
		
		if (effectiveProxyHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(effectiveProxyHost).find()) {
			throw new ConfigurationException("Configuring of cf.proxyHost or cf.proxy.host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}
		
		if (effectiveProxyHost != null && effectiveProxyPort != 0) {
			
			String proxyIP = null;
			if (!InetAddressUtils.isIPv4Address(effectiveProxyHost) && !InetAddressUtils.isIPv6Address(effectiveProxyHost)) {
				/*
				 * NB: There is currently a bug in io.netty.util.internal.SocketUtils.connect()
				 * which is called implicitly by the CF API Client library, which leads to the effect
				 * that a hostname for the proxy isn't resolved. Thus, it is only possible to pass 
				 * IP addresses as proxy names.
				 * To work around this issue, we manually perform a resolution of the hostname here
				 * and then feed that one to the CF API Client library...
				 */
				try {
					InetAddress ia = InetAddress.getByName(effectiveProxyHost);
					proxyIP = ia.getHostAddress();
				} catch (UnknownHostException e) {
					throw new ConfigurationException(String.format("The proxy host '%s' cannot be resolved to an IP address; is there a typo in your configuration?", effectiveProxyHost), e);
				}
			} else {
				// the address specified is already an IP address
				proxyIP = effectiveProxyHost;
			}
			
			return ProxyConfiguration.builder().host(proxyIP).port(effectiveProxyPort).build();
			
		} else {
			return null;
		}
	}
	
	private ReactorCloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorCloudFoundryClient.builder().connectionContext(connectionContext).tokenProvider(tokenProvider).build();
	}
	
	@PostConstruct
	@SuppressWarnings("unused")
	private void constructCloudFoundryClient() {
		this.reset();
		
		String rootV3EndpointURL = this.cloudFoundryClient.getRootV3().block();
		if (Strings.isEmpty(rootV3EndpointURL)) {
			log.error("Unable to get v3 info endpoint of CF platform. This version requires that CF API V3 is supported");
			throw new UnsupportedOperationException("CF API V3 is not supported");
		}
		
		log.info("Using Cloud Controller API V3 at {}", rootV3EndpointURL);
	}

	@Override
	public void reset() {
		try {
			if (this.cloudFoundryClient != null) {
				/*
				 * Note: there may still be connections and threads open, which need to be closed.
				 * https://github.com/promregator/promregator/issues/161 pointed that out.
				 */
				final ConnectionContext connectionContext = this.cloudFoundryClient.getConnectionContext();
				// Note: connectionContext is ensured to be non-null
				if (connectionContext instanceof DefaultConnectionContext dcc) {
					/*
					 * For the idea see also 
					 * https://github.com/cloudfoundry/cf-java-client/issues/777 and
					 * https://issues.jenkins-ci.org/browse/JENKINS-53136
					 */
					dcc.dispose();
				}
			}
			
			ProxyConfiguration proxyConfiguration = this.proxyConfiguration();
			DefaultConnectionContext connectionContext = this.connectionContext(proxyConfiguration);
			PasswordGrantTokenProvider tokenProvider = this.tokenProvider();
			
			this.cloudFoundryClient = this.cloudFoundryClient(connectionContext, tokenProvider);
		} catch (ConfigurationException e) {
			log.error("Restarting Cloud Foundry Client failed due to Configuration Exception raised", e);
		}
	}

	@PostConstruct
	@SuppressWarnings("unused")
	private void setupPaginatedRequestFetcher() {
		this.paginatedRequestFetcher = new ReactiveCFPaginatedRequestFetcher(this.internalMetrics, this.requestRateLimit, 
				Duration.ofMillis(this.backoffDelay));
	}

	@Override
	public Mono<InfoV3> getInfo() {
		ReactorInfoV3 reactorInfoV3 = new ReactorInfoV3(this.cloudFoundryClient.getConnectionContext(), this.cloudFoundryClient.getRootV3(), 
				this.cloudFoundryClient.getTokenProvider(), this.cloudFoundryClient.getRequestTags());
		
		return reactorInfoV3.get();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request.
		org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest orgsRequest = org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest.builder().name(orgName).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.ORG, orgName, orgsRequest,
				or -> this.cloudFoundryClient.organizationsV3().list(or), this.requestTimeoutOrg);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		PaginatedRequestGeneratorFunctionV3<org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest> requestGenerator = (resultsPerPage, pageNumber) ->
			org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest.builder()
					.perPage(resultsPerPage)
					.page(pageNumber)
					.build();

		PaginatedResponseGeneratorFunctionV3<org.cloudfoundry.client.v3.organizations.OrganizationResource, org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> responseGenerator = (list, numberOfPages) ->
			org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse.builder()
					.addAllResources(list)
					.pagination(Pagination.builder().totalPages(numberOfPages).totalResults(list.size()).build())
					.build();

		return this.paginatedRequestFetcher.performGenericPagedRetrievalV3(RequestType.ALL_ORGS, "(empty)", requestGenerator,
					r -> this.cloudFoundryClient.organizationsV3().list(r),  this.requestTimeoutOrg, responseGenerator);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request.

		String key = String.format("%s|%s", orgId, spaceName);

		org.cloudfoundry.client.v3.spaces.ListSpacesRequest spacesRequest = org.cloudfoundry.client.v3.spaces.ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.SPACE, key, spacesRequest, sr -> this.cloudFoundryClient.spacesV3().list(sr),
					this.requestTimeoutSpace);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		PaginatedRequestGeneratorFunctionV3<org.cloudfoundry.client.v3.spaces.ListSpacesRequest> requestGenerator = (resultsPerPage, pageNumber) ->
			org.cloudfoundry.client.v3.spaces.ListSpacesRequest.builder()
					.organizationId(orgId)
					.perPage(resultsPerPage)
					.page(pageNumber)
					.build();

		PaginatedResponseGeneratorFunctionV3<org.cloudfoundry.client.v3.spaces.SpaceResource, org.cloudfoundry.client.v3.spaces.ListSpacesResponse> responseGenerator = (list, numberOfPages) ->
			org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder()
					.addAllResources(list)
					.pagination(Pagination.builder().totalPages(numberOfPages).totalResults(list.size()).build())
					.build();


		return this.paginatedRequestFetcher.performGenericPagedRetrievalV3(RequestType.SPACE_IN_ORG, orgId, requestGenerator,
					r -> this.cloudFoundryClient.spacesV3().list(r),  this.requestTimeoutSpace, responseGenerator);
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
		String key = String.format("%s|%s", orgId, spaceId);

		PaginatedRequestGeneratorFunctionV3<org.cloudfoundry.client.v3.applications.ListApplicationsRequest> requestGenerator = (resultsPerPage, pageNumber) ->
			org.cloudfoundry.client.v3.applications.ListApplicationsRequest.builder()
					.organizationId(orgId)
					.spaceId(spaceId)
					.perPage(resultsPerPage)
					.page(pageNumber)
					.build();

		PaginatedResponseGeneratorFunctionV3<ApplicationResource, ListApplicationsResponse> responseGenerator = (list, numberOfPages) ->
			ListApplicationsResponse.builder()
				.addAllResources(list)
				.pagination(Pagination.builder().totalPages(numberOfPages).totalResults(list.size()).build())
				.build();

		return this.paginatedRequestFetcher.performGenericPagedRetrievalV3(RequestType.ALL_APPS_IN_SPACE, key, requestGenerator,
				r -> this.cloudFoundryClient.applicationsV3().list(r), this.requestTimeoutAppInSpace, responseGenerator);
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsRequest request = org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsRequest.builder().organizationId(orgId).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.DOMAINS, orgId,
				request, r -> this.cloudFoundryClient.organizationsV3().listDomains(request), this.requestTimeoutDomains);
	}

	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId) {
		// not necessary to be implemented, because Caffeine cache converts it into retrieveRoutesForAppIds() requests
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds) {
		PaginatedRequestGeneratorFunctionV3<ListRoutesRequest> requestGenerator = (resultsPerPage, pageNumber) ->
			ListRoutesRequest.builder()
				.addAllApplicationIds(appIds)
				.perPage(MAXIMAL_NUMBER_OF_ROUTES_PER_CAPI_REQUEST)
				.build();
		
		PaginatedResponseGeneratorFunctionV3<org.cloudfoundry.client.v3.routes.RouteResource, ListRoutesResponse> responseGenerator = (list, numberOfPages) -> 
			ListRoutesResponse.builder()
				.addAllResources(list)
				.pagination(Pagination.builder().totalPages(numberOfPages).totalResults(list.size()).build())
				.build();
		
		return this.paginatedRequestFetcher.performGenericPagedRetrievalV3(RequestType.ROUTES, appIds, requestGenerator, 
				r -> this.cloudFoundryClient.routesV3().list(r), this.requestTimeoutRoute, 
				responseGenerator);
	}
	
	@Override
	public Mono<ListProcessesResponse> retrieveWebProcessesForAppId(String applicationId) {
		PaginatedRequestGeneratorFunctionV3<ListProcessesRequest> requestGenerator = (resultsPerPage, pageNumber) ->
			ListProcessesRequest.builder()
				.applicationId(applicationId)
				.type(CF_API_V3_PROCESS_TYPE_WEB)
				.build();
		
		PaginatedResponseGeneratorFunctionV3<org.cloudfoundry.client.v3.processes.ProcessResource, ListProcessesResponse> responseGenerator = (list, numberOfPages) -> {
			if (log.isDebugEnabled()) {
				log.debug("Received process page in responseGenerator with {} items - total number of pages: {}", list.size(), numberOfPages);
				final boolean duplicateEntryDetected = list.stream().distinct().collect(Collectors.toList()).size() == list.size();
				log.debug("Detection of duplicate entries in responseGenerator: {}", Boolean.valueOf(duplicateEntryDetected).toString());
			}
			return ListProcessesResponse.builder()
			.addAllResources(list)
			.pagination(Pagination.builder().totalPages(numberOfPages).totalResults(list.size()).build())
			.build();
		};
		
		return this.paginatedRequestFetcher.performGenericPagedRetrievalV3(RequestType.PROCESSES, applicationId, requestGenerator, 
				r -> { 
					Mono<ListProcessesResponse> monoResponse = this.cloudFoundryClient.processes().list(r);
					if (log.isDebugEnabled()) {
						monoResponse = monoResponse.doOnNext(e -> {
							final List<ProcessResource> list = e.getResources();
							log.debug("Received process page in requestFunction with {} items", list.size());
							final boolean duplicateEntryDetected = list.stream().distinct().collect(Collectors.toList()).size() == list.size();
							log.debug("Detection of duplicate entries in requestFunction: {}", Boolean.valueOf(duplicateEntryDetected).toString());
						});
					}
					return monoResponse;
				}, this.requestTimeoutProcess,
				responseGenerator);
		
	}

	@Override
	public Mono<ListProcessesResponse> retrieveWebProcessesForAppIds(Set<String> applicationIds) {
		PaginatedRequestGeneratorFunctionV3<ListProcessesRequest> requestGenerator = (resultsPerPage, pageNumber) ->
		ListProcessesRequest.builder()
			.addAllApplicationIds(applicationIds)
			.type(CF_API_V3_PROCESS_TYPE_WEB)
			.build();
	
		PaginatedResponseGeneratorFunctionV3<org.cloudfoundry.client.v3.processes.ProcessResource, ListProcessesResponse> responseGenerator = (list, numberOfPages) -> 
			ListProcessesResponse.builder()
			.addAllResources(list)
			.pagination(Pagination.builder().totalPages(numberOfPages).totalResults(list.size()).build())
			.build();
		
		return this.paginatedRequestFetcher.performGenericPagedRetrievalV3(RequestType.PROCESSES, applicationIds, requestGenerator, 
				r -> this.cloudFoundryClient.processes().list(r), this.requestTimeoutProcess, 
				responseGenerator);
	}


}
