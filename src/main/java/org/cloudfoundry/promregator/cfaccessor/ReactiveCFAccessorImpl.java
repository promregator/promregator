package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.http.conn.util.InetAddressUtils;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoRequest;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryRequest;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.promregator.config.ApiConfig;
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
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

import reactor.core.publisher.Mono;

public class ReactiveCFAccessorImpl implements CFAccessor {

	private static final Logger log = LoggerFactory.getLogger(ReactiveCFAccessorImpl.class);
	private final CloudFoundryConfiguration cf;
	private final PromregatorConfiguration promregatorConfiguration;

	private InternalMetrics internalMetrics;

	public ReactiveCFAccessorImpl(CloudFoundryConfiguration cf,
								  PromregatorConfiguration promregatorConfiguration,
								  InternalMetrics internalMetrics) {
		this.cf = cf;
		this.promregatorConfiguration = promregatorConfiguration;
		this.internalMetrics = internalMetrics;
		createClients();
	}

	private static final Pattern PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
	
	private Map<String,ReactorCloudFoundryClient> cloudFoundryClients = new ConcurrentHashMap<>();
	private ReactiveCFPaginatedRequestFetcher paginatedRequestFetcher;
	
	private DefaultConnectionContext connectionContext(ApiConfig apiConfig, ProxyConfiguration proxyConfiguration) throws ConfigurationException {
		if (PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(apiConfig.getHost()).find()) {
			throw new ConfigurationException("cf.api_host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}

		Builder connctx = DefaultConnectionContext.builder()
				.apiHost(apiConfig.getHost())
				.skipSslValidation(apiConfig.getSkipSslValidation());
		
		if (proxyConfiguration != null) {
			connctx = connctx.proxyConfiguration(proxyConfiguration);
		}
		
		if (apiConfig.getConnectionPool().getSize() != null) {
			connctx = connctx.connectionPoolSize(apiConfig.getConnectionPool().getSize());
		}
		
		if (apiConfig.getThreadPool().getSize() != null) {
			connctx = connctx.threadPoolSize(apiConfig.getThreadPool().getSize());
		}
		
		return connctx.build();
	}

	private PasswordGrantTokenProvider tokenProvider(ApiConfig apiConfig) {
		return PasswordGrantTokenProvider.builder().password(apiConfig.getPassword()).username(apiConfig.getUsername()).build();
	}

	private ProxyConfiguration proxyConfiguration(ApiConfig apiConfig) throws ConfigurationException {
		
		String effectiveProxyHost = null;
		int effectiveProxyPort = 0;
		
		if (apiConfig.getProxy() != null && apiConfig.getProxy().getHost() != null && apiConfig.getProxy().getPort() != null) {
			// used the new way of defining proxies
			effectiveProxyHost = apiConfig.getProxy().getHost();
			effectiveProxyPort = apiConfig.getProxy().getPort();
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

	private void createClients(){
		cf.getApi().keySet().forEach(this::reset);

		if (promregatorConfiguration.getInternal().getPreCheckAPIVersion()) {
			GetInfoRequest request = GetInfoRequest.builder().build();
			cloudFoundryClients.forEach((api, client) -> {
				GetInfoResponse getInfo = client.info().get(request).block();
				if(getInfo == null) throw new RuntimeException("Error connecting to CF api '"+api+"'");
				// NB: This also ensures that the connection has been established properly...
				log.info("Target CF platform ({}) is running on API version {}", api, getInfo.getApiVersion());
			});
		}
	}

	@Override
	public void reset(String api) {
		ApiConfig apiConfig = this.cf.getApi().get(api);
		resetCloudFoundryClient(api, apiConfig);
	}


	public void resetCloudFoundryClient(String api, ApiConfig apiConfig) {
		try {
			ProxyConfiguration proxyConfiguration = this.proxyConfiguration(apiConfig);
			DefaultConnectionContext connectionContext = this.connectionContext(apiConfig, proxyConfiguration);
			PasswordGrantTokenProvider tokenProvider = this.tokenProvider(apiConfig);
		
			this.cloudFoundryClients.put(api,this.cloudFoundryClient(connectionContext, tokenProvider));
		} catch (ConfigurationException e) {
			log.error("Restarting Cloud Foundry Client failed due to Configuration Exception raised", e);
		}
	}

	@PostConstruct
	@SuppressWarnings("unused")
	private void setupPaginatedRequestFetcher() {
		this.paginatedRequestFetcher = new ReactiveCFPaginatedRequestFetcher(this.internalMetrics);
	}
	
	private static final GetInfoRequest DUMMY_GET_INFO_REQUEST = GetInfoRequest.builder().build();
	
	@Override
	public Mono<GetInfoResponse> getInfo(String api) {
		return this.cloudFoundryClients.get(api).info().get(DUMMY_GET_INFO_REQUEST);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveOrgId(java.lang.String)
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String api, String orgName) {
		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request. 
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("org", "retrieveOrgId", orgName, orgsRequest,
				or -> this.cloudFoundryClients.get(api).organizations()
				          .list(or), this.cf.getRequest().getTimeout().getOrg());
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllOrgIds()
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds(String api) {
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
		
		return this.paginatedRequestFetcher.performGenericPagedRetrieval("allOrgs", "retrieveAllOrgIds", "(empty)", requestGenerator, 
				r -> this.cloudFoundryClients.get(api).organizations().list(r),  this.cf.getRequest().getTimeout().getOrg(), responseGenerator);
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceId(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String api, String orgId, String spaceName) {
		// Note: even though we use the List request here, the number of values returned is either zero or one
		// ==> No need for a paged request. 
		
		String key = String.format("%s|%s", orgId, spaceName);
		
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("space", "retrieveSpaceId", key, spacesRequest, sr ->
				this.cloudFoundryClients.get(api).spaces().list(sr),
				this.cf.getRequest().getTimeout().getSpace());
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceIdsInOrg(java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String api, String orgId) {
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

		
		return this.paginatedRequestFetcher.performGenericPagedRetrieval("space", "retrieveAllSpaceIdsInOrg", orgId, requestGenerator, 
				r -> this.cloudFoundryClients.get(api).spaces().list(r),  this.cf.getRequest().getTimeout().getSpace(), responseGenerator);
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllApplicationIdsInSpace(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String api, String orgId, String spaceId) {
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
		
		return this.paginatedRequestFetcher.performGenericPagedRetrieval("allApps", "retrieveAllApplicationIdsInSpace", key, requestGenerator, 
				r -> this.cloudFoundryClients.get(api).applicationsV2().list(r),  this.cf.getRequest().getTimeout().getAppInSpace(), responseGenerator);
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String api, String spaceId) {
		// Note that GetSpaceSummaryRequest is not paginated
		
		GetSpaceSummaryRequest request = GetSpaceSummaryRequest.builder().spaceId(spaceId).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("spaceSummary", "retrieveSpaceSummary", spaceId, 
				request, r -> this.cloudFoundryClients.get(api).spaces().getSummary(r), this.cf.getRequest().getTimeout().getAppSummary());

	}

}
