package org.cloudfoundry.promregator.cfaccessor;

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
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class CFAccessorImpl implements CFAccessor {

	@Autowired
	private ReactorCloudFoundryClient cloudFoundryClient;

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveOrgId(java.lang.String)
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
		return this.cloudFoundryClient.organizations().list(orgsRequest).log("Query Org");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceId(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
		return this.cloudFoundryClient.spaces().list(spacesRequest).log("Query Space");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveApplicationId(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName) {
		ListApplicationsRequest request = ListApplicationsRequest.builder()
				.organizationId(orgId)
				.spaceId(spaceId)
				.name(applicationName)
				.build();
		return this.cloudFoundryClient.applicationsV2().list(request).log("Query App");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRouteMapping(java.lang.String)
	 */
	@Override
	public Mono<ListRouteMappingsResponse> retrieveRouteMapping(String appId) {
		ListRouteMappingsRequest mappingRequest = ListRouteMappingsRequest.builder().applicationId(appId).build();
		return this.cloudFoundryClient.routeMappings().list(mappingRequest).log("Query Route Mapping");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRoute(java.lang.String)
	 */
	@Override
	public Mono<GetRouteResponse> retrieveRoute(String routeId) {
		GetRouteRequest getRequest = GetRouteRequest.builder().routeId(routeId).build();
		return this.cloudFoundryClient.routes().get(getRequest).log("Get Route");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSharedDomain(java.lang.String)
	 */
	@Override
	public Mono<GetSharedDomainResponse> retrieveSharedDomain(String domainId) {
		GetSharedDomainRequest domainRequest = GetSharedDomainRequest.builder().sharedDomainId(domainId).build();
		return this.cloudFoundryClient.sharedDomains().get(domainRequest).log("Get Domain");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveProcesses(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListProcessesResponse> retrieveProcesses(String orgId, String spaceId, String appId) {
		ListProcessesRequest request = ListProcessesRequest.builder().organizationId(orgId).spaceId(spaceId).applicationId(appId).build();
		return this.cloudFoundryClient.processes().list(request).log("List Processes");
	}
}
