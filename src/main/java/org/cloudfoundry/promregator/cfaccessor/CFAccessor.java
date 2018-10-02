package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.applications.GetApplicationResponse;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstancesResponse;

import reactor.core.publisher.Mono;

public interface CFAccessor {

	Mono<ListOrganizationsResponse> retrieveOrgId(String orgName);
	
	Mono<ListOrganizationsResponse> retrieveAllOrgIds();

	Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName);
	
	Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId);

	Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId);

	Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId);
	
	Mono<ListUserProvidedServiceInstancesResponse> retrieveAllUserProvidedService();
	
	Mono<GetSpaceResponse> retrieveSpace(String spaceId);
	
	Mono<GetOrganizationResponse> retrieveOrg(String orgId);
	
	Mono<ListUserProvidedServiceInstanceServiceBindingsResponse> retrieveUserProvidedServiceBindings(String upsId);
}