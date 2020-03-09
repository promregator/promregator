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
	private final CFApiClients apiClients;

	public ReactiveCFAccessorImpl(CloudFoundryConfiguration cf,
								  PromregatorConfiguration promregatorConfiguration,
								  InternalMetrics internalMetrics,
								  CFApiClients apiClients) {
		this.cf = cf;
		this.promregatorConfiguration = promregatorConfiguration;
		this.internalMetrics = internalMetrics;
		this.apiClients = apiClients;
	}

	private ReactiveCFPaginatedRequestFetcher paginatedRequestFetcher;

	@PostConstruct
	@SuppressWarnings("unused")
	private void setupPaginatedRequestFetcher() {
		this.paginatedRequestFetcher = new ReactiveCFPaginatedRequestFetcher(this.internalMetrics);
	}
	
	private static final GetInfoRequest DUMMY_GET_INFO_REQUEST = GetInfoRequest.builder().build();
	
	@Override
	public Mono<GetInfoResponse> getInfo(String api) {
		return this.apiClients.getClient(api).info().get(DUMMY_GET_INFO_REQUEST);
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
				or -> this.apiClients.getClient(api).organizations()
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
				r -> this.apiClients.getClient(api).organizations().list(r),  this.cf.getRequest().getTimeout().getOrg(), responseGenerator);
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
				this.apiClients.getClient(api).spaces().list(sr),
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
				r -> this.apiClients.getClient(api).spaces().list(r),  this.cf.getRequest().getTimeout().getSpace(), responseGenerator);
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
				r -> this.apiClients.getClient(api).applicationsV2().list(r),  this.cf.getRequest().getTimeout().getAppInSpace(), responseGenerator);
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String api, String spaceId) {
		// Note that GetSpaceSummaryRequest is not paginated
		
		GetSpaceSummaryRequest request = GetSpaceSummaryRequest.builder().spaceId(spaceId).build();
		
		return this.paginatedRequestFetcher.performGenericRetrieval("spaceSummary", "retrieveSpaceSummary", spaceId, 
				request, r -> this.apiClients.getClient(api).spaces().getSummary(r), this.cf.getRequest().getTimeout().getAppSummary());

	}

}
