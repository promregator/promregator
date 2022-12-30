package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;

import reactor.core.publisher.Mono;

public interface CFAccessor {
	Mono<GetInfoResponse> getInfo();
	
	Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId);	

	Mono<ListOrganizationDomainsResponse> retrieveAllDomains(String orgId);

	Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName);

	Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3();

	Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName);

	Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId);

	Mono<org.cloudfoundry.client.v3.applications.ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId);

	Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId);

	Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId);

	Mono<org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId);

	void reset();
}
