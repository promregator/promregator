package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;

import reactor.core.publisher.Mono;

public interface CFAccessor {
	Mono<GetInfoResponse> getInfo(String api);
	
	Mono<ListOrganizationsResponse> retrieveOrgId(String api, String orgName);
	
	Mono<ListOrganizationsResponse> retrieveAllOrgIds(String api);

	Mono<ListSpacesResponse> retrieveSpaceId(String api, String orgId, String spaceName);
	
	Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String api, String orgId);

	Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String api, String orgId, String spaceId);

	Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String api, String spaceId);
}