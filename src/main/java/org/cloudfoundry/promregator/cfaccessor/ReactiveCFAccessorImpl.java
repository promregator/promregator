package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoRequest;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryRequest;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
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

import reactor.core.publisher.Mono;

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
	private ReactiveCFPaginatedRequestFetcher paginatedRequestFetcher;

	
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

	@PostConstruct
	private void setupPaginatedRequestFetcher() {
		this.paginatedRequestFetcher = new ReactiveCFPaginatedRequestFetcher(this.internalMetrics);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveOrgId(java.lang.String)
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("org", "retrieveOrgId", orgName, orgsRequest, or -> {
			return this.cloudFoundryClient.organizations().list(or);
		}, this.requestTimeoutOrg);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllOrgIds()
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("allOrgs", "retrieveAllOrgIds", "(empty)", orgsRequest, or -> {
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
		
		return this.paginatedRequestFetcher.performGenericRetrieval("space", "retrieveSpaceId", key, spacesRequest, sr -> {
			return this.cloudFoundryClient.spaces().list(sr);
		}, this.requestTimeoutSpace);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceIdsInOrg(java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("space", "retrieveAllSpaceIdsInOrg", orgId, spacesRequest, sr -> {
			return this.cloudFoundryClient.spaces().list(sr);
		}, this.requestTimeoutSpace);
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllApplicationIdsInSpace(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		String key = String.format("%s|%s", orgId, spaceId);
		
		PaginatedRequestGeneratorFunction<ListApplicationsRequest> requestGenerator = (orderDirection, resultsPerPage, pageNumber) -> {
			ListApplicationsRequest request = ListApplicationsRequest.builder()
				.organizationId(orgId)
				.spaceId(spaceId)
				.orderDirection(orderDirection)
				.resultsPerPage(resultsPerPage)
				.page(pageNumber)
				.build();
			
			return request;
		};
		
		PaginatedResponseGeneratorFunction<ApplicationResource, ListApplicationsResponse> responseGenerator = (list, numberOfPages) -> {
			return ListApplicationsResponse.builder()
				.addAllResources(list)
				.totalPages(numberOfPages)
				.totalResults(list.size())
				.build();
		};
		
		return this.paginatedRequestFetcher.performGenericPagedRetrieval("allApps", "retrieveAllApplicationIdsInSpace", key, requestGenerator, 
				r -> this.cloudFoundryClient.applicationsV2().list(r),  this.requestTimeoutAppInSpace, responseGenerator);
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		GetSpaceSummaryRequest request = GetSpaceSummaryRequest.builder().spaceId(spaceId).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("spaceSummary", "retrieveSpaceSummary", spaceId, 
				request, r -> this.cloudFoundryClient.spaces().getSummary(r), this.requestTimeoutAppSummary);

	}

}
