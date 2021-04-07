package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.http.conn.util.InetAddressUtils;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoRequest;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryRequest;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.Pagination;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceRequest;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
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

	private static final Logger log = LoggerFactory.getLogger(ReactiveCFAccessorImpl.class);
	private boolean v3Enabled;
	public static org.cloudfoundry.client.v3.applications.ListApplicationsResponse INVALID_APPLICATIONS_RESPONSE = org.cloudfoundry.client.v3.applications.ListApplicationsResponse.builder().build();

	@Value("${cf.api_host}")
	private String apiHost;

	@Value("${cf.username}")
	private String username;
	
	@Value("${cf.password}")
	private String password;
	
	@Value("${cf.skipSslValidation:false}")
	private boolean skipSSLValidation;

	/**
	 * The hostname of the HTTP proxy based on the deprecated configuration option <pre>cf.proxyHost</pre>.
	 * @deprecated use <pre>proxyHost</pre> instead.
	 */
	@Value("${cf.proxyHost:#{null}}")
	@Deprecated
	private String proxyHostDeprecated;

	/**
	 * The port of the HTTP proxy based on the deprecated configuration option <pre>cf.proxyPort</pre>.
	 * @deprecated use <pre>proxyPort</pre> instead.
	 */
	@Value("${cf.proxyPort:0}")
	@Deprecated
	private int proxyPortDeprecated;

	@Value("${cf.proxy.host:#{null}}") 
	private String proxyHost;
	
	@Value("${cf.proxy.port:0}") 
	private int proxyPort;
	
	@Value("${cf.request.timeout.org:2500}")
	private int requestTimeoutOrg;

	@Value("${cf.request.timeout.space:2500}")
	private int requestTimeoutSpace;

	@Value("${cf.request.timeout.appInSpace:2500}")
	private int requestTimeoutAppInSpace;
	
	@Value("${cf.request.timeout.appSummary:4000}")
	private int requestTimeoutAppSummary;
	
	@Value("${cf.request.timeout.domain:2500}")
	private int requestTimeoutDomains;
	
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
		
		String effectiveProxyHost;
		int effectiveProxyPort;
		
		if (this.proxyHost != null && this.proxyPort != 0) {
			// used the new way of defining proxies
			effectiveProxyHost = this.proxyHost;
			effectiveProxyPort = this.proxyPort;
		} else {
			// the old way *may* be used
			effectiveProxyHost = this.proxyHostDeprecated;
			effectiveProxyPort = this.proxyPortDeprecated;
		}
		
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
	private void constructCloudFoundryClient() throws ConfigurationException {
		this.reset();
		
		if (this.performPrecheckOfAPIVersion) {
			GetInfoRequest request = GetInfoRequest.builder().build();
			GetInfoResponse getInfo = this.cloudFoundryClient.info().get(request).block();
			// NB: This also ensures that the connection has been established properly...
			
			if (getInfo == null) {
				log.warn("unable to get info endpoint of CF platform");
				return;
			} 

			String apiVersion = getInfo.getApiVersion();
			if (apiVersion == null) {
				log.warn("Target CF Platform did not provide a proper API version");
				return;
			}
			
			log.info("Target CF platform is running on API version {}", apiVersion);
		}

		// Ensures v3 API exists. The CF Java Client does not yet implement the info endpoint for V3, so we do it manually.
		JsonNode v3Info = new ReactorInfoV3(this.cloudFoundryClient.getConnectionContext(), this.cloudFoundryClient.getRootV3(),
											this.cloudFoundryClient.getTokenProvider(), this.cloudFoundryClient.getRequestTags())
			.get().onErrorReturn(JsonNodeFactory.instance.nullNode()).block();

		if (v3Info == null || v3Info.isNull()) {
			log.warn("unable to get v3 info endpoint of CF platform");
			this.v3Enabled = false;
		} else {
			this.v3Enabled = true;
		}
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
				if (connectionContext instanceof DefaultConnectionContext) {
					/*
					 * For the idea see also 
					 * https://github.com/cloudfoundry/cf-java-client/issues/777 and
					 * https://issues.jenkins-ci.org/browse/JENKINS-53136
					 */
					DefaultConnectionContext dcc = (DefaultConnectionContext) connectionContext;
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

	public boolean isV3Enabled() {
		return this.v3Enabled;
	}
	
	private static final GetInfoRequest DUMMY_GET_INFO_REQUEST = GetInfoRequest.builder().build();
	
	@Override
	public Mono<GetInfoResponse> getInfo() {
		return this.cloudFoundryClient.info().get(DUMMY_GET_INFO_REQUEST);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveOrgId(java.lang.String)
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request. 
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.ORG, orgName, orgsRequest,
				or -> this.cloudFoundryClient.organizations()
				          .list(or), this.requestTimeoutOrg);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllOrgIds()
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		PaginatedRequestGeneratorFunction<ListOrganizationsRequest> requestGenerator = (orderDirection, resultsPerPage, pageNumber) ->
			ListOrganizationsRequest.builder()
				.orderDirection(orderDirection)
				.resultsPerPage(resultsPerPage)
				.page(pageNumber)
				.build();
		
		PaginatedResponseGeneratorFunction<OrganizationResource, ListOrganizationsResponse> responseGenerator = (list, numberOfPages) ->
				ListOrganizationsResponse.builder()
				.addAllResources(list)
				.totalPages(numberOfPages)
				.totalResults(list.size())
				.build();
		
		return this.paginatedRequestFetcher.performGenericPagedRetrieval(RequestType.ALL_ORGS, "(empty)", requestGenerator, 
				r -> this.cloudFoundryClient.organizations().list(r),  this.requestTimeoutOrg, responseGenerator);
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceId(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request. 
		
		String key = String.format("%s|%s", orgId, spaceName);
		
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.SPACE, key, spacesRequest, sr ->
				this.cloudFoundryClient.spaces().list(sr),
				this.requestTimeoutSpace);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceIdsInOrg(java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		PaginatedRequestGeneratorFunction<ListSpacesRequest> requestGenerator = (orderDirection, resultsPerPage, pageNumber) ->
			ListSpacesRequest.builder()
				.organizationId(orgId)
				.orderDirection(orderDirection)
				.resultsPerPage(resultsPerPage)
				.page(pageNumber)
				.build();
		
		PaginatedResponseGeneratorFunction<SpaceResource, ListSpacesResponse> responseGenerator = (list, numberOfPages) ->
				ListSpacesResponse.builder()
				.addAllResources(list)
				.totalPages(numberOfPages)
				.totalResults(list.size())
				.build();

		
		return this.paginatedRequestFetcher.performGenericPagedRetrieval(RequestType.SPACE_IN_ORG, orgId, requestGenerator, 
				r -> this.cloudFoundryClient.spaces().list(r),  this.requestTimeoutSpace, responseGenerator);
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllApplicationIdsInSpace(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		String key = String.format("%s|%s", orgId, spaceId);
		
		PaginatedRequestGeneratorFunction<ListApplicationsRequest> requestGenerator = (orderDirection, resultsPerPage, pageNumber) ->
			ListApplicationsRequest.builder()
				.organizationId(orgId)
				.spaceId(spaceId)
				.orderDirection(orderDirection)
				.resultsPerPage(resultsPerPage)
				.page(pageNumber)
				.build();
		
		PaginatedResponseGeneratorFunction<ApplicationResource, ListApplicationsResponse> responseGenerator = (list, numberOfPages) ->
				ListApplicationsResponse.builder()
				.addAllResources(list)
				.totalPages(numberOfPages)
				.totalResults(list.size())
				.build();
		
		return this.paginatedRequestFetcher.performGenericPagedRetrieval(RequestType.ALL_APPS_IN_SPACE, key, requestGenerator, 
				r -> this.cloudFoundryClient.applicationsV2().list(r), this.requestTimeoutAppInSpace, responseGenerator);
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		// Note that GetSpaceSummaryRequest is not paginated
		
		GetSpaceSummaryRequest request = GetSpaceSummaryRequest.builder().spaceId(spaceId).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.SPACE_SUMMARY, spaceId, 
				request, r -> this.cloudFoundryClient.spaces().getSummary(r), this.requestTimeoutAppSummary);

  }

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomains(String orgId) {

		ListOrganizationDomainsRequest request = ListOrganizationDomainsRequest.builder().organizationId(orgId).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.DOMAINS, orgId, 
		request, r -> this.cloudFoundryClient.organizations().listDomains(request), this.requestTimeoutDomains);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		if (!isV3Enabled()) {
			throw new UnsupportedOperationException("V3 API is not supported on your foundation.");
		}

		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request.
		org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest orgsRequest = org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest.builder().name(orgName).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.ORG, orgName, orgsRequest,
																	or -> this.cloudFoundryClient.organizationsV3()
																								 .list(or), this.requestTimeoutOrg);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		if (!isV3Enabled()) {
			throw new UnsupportedOperationException("V3 API is not supported on your foundation.");
		}

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
		if (!isV3Enabled()) {
			throw new UnsupportedOperationException("V3 API is not supported on your foundation.");
		}

		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request.

		String key = String.format("%s|%s", orgId, spaceName);

		org.cloudfoundry.client.v3.spaces.ListSpacesRequest spacesRequest = org.cloudfoundry.client.v3.spaces.ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.SPACE, key, spacesRequest, sr ->
																		this.cloudFoundryClient.spacesV3().list(sr),
																	this.requestTimeoutSpace);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		if (!isV3Enabled()) {
			throw new UnsupportedOperationException("V3 API is not supported on your foundation.");
		}

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
	public Mono<org.cloudfoundry.client.v3.applications.ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
		if (!isV3Enabled()) {
			return Mono.just(INVALID_APPLICATIONS_RESPONSE);
		}

		String key = String.format("%s|%s", orgId, spaceId);

		PaginatedRequestGeneratorFunctionV3<org.cloudfoundry.client.v3.applications.ListApplicationsRequest> requestGenerator = (resultsPerPage, pageNumber) ->
			org.cloudfoundry.client.v3.applications.ListApplicationsRequest.builder()
								   .organizationId(orgId)
								   .spaceId(spaceId)
								   .perPage(resultsPerPage)
								   .page(pageNumber)
								   .build();

		PaginatedResponseGeneratorFunctionV3<org.cloudfoundry.client.v3.applications.ApplicationResource, org.cloudfoundry.client.v3.applications.ListApplicationsResponse> responseGenerator = (list, numberOfPages) ->
			org.cloudfoundry.client.v3.applications.ListApplicationsResponse.builder()
									.addAllResources(list)
									.pagination(Pagination.builder().totalPages(numberOfPages).totalResults(list.size()).build())
									.build();

		return this.paginatedRequestFetcher.performGenericPagedRetrievalV3(RequestType.ALL_APPS_IN_SPACE, key, requestGenerator,
																		 r -> this.cloudFoundryClient.applicationsV3().list(r), this.requestTimeoutAppInSpace, responseGenerator);
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
		if (!isV3Enabled()) {
			throw new UnsupportedOperationException("V3 API is not supported on your foundation.");
		}

		// This API has drastically changed in v3 and does not support the same resources. This call for a space summary will probably
		// take another call to list applications for a space, list routes for the apps, and list domains in the org
		// Previously they were all grouped into this API

		GetSpaceRequest request = GetSpaceRequest.builder().spaceId(spaceId).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.SPACE_SUMMARY, spaceId,
																	request, r -> this.cloudFoundryClient.spacesV3().get(r), this.requestTimeoutAppSummary);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		if (!isV3Enabled()) {
			throw new UnsupportedOperationException("V3 API is not supported on your foundation.");
		}

		org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsRequest request = org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsRequest.builder().organizationId(orgId).build();

		return this.paginatedRequestFetcher.performGenericRetrieval(RequestType.DOMAINS, orgId,
																	request, r -> this.cloudFoundryClient.organizationsV3().listDomains(request), this.requestTimeoutDomains);
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId) {
		throw new UnsupportedOperationException();
	}
}
