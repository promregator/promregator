package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsResponse;
import org.cloudfoundry.client.v2.routes.GetRouteResponse;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainResponse;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;

import reactor.core.publisher.Mono;

public interface CFAccessor {

	Mono<ListOrganizationsResponse> retrieveOrgId(String orgName);

	Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName);

	Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName);

	Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId);
	
	Mono<ListRouteMappingsResponse> retrieveRouteMapping(String appId);

	Mono<GetRouteResponse> retrieveRoute(String routeId);

	Mono<GetSharedDomainResponse> retrieveSharedDomain(String domainId);

	Mono<ListProcessesResponse> retrieveProcesses(String orgId, String spaceId, String appId);

}