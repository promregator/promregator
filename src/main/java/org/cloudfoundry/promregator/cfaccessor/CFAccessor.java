package org.cloudfoundry.promregator.cfaccessor;

import java.util.Set;

import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.cfaccessor.client.InfoV3;

import reactor.core.publisher.Mono;

public interface CFAccessor {
	Mono<InfoV3> getInfo();
	
	Mono<ListOrganizationsResponse> retrieveOrgIdV3(String orgName);

	Mono<ListOrganizationsResponse> retrieveAllOrgIdsV3();

	Mono<ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName);

	Mono<ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId);

	Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId);

	Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId);

	Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId);
	
	Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds);
	
	Mono<ListProcessesResponse> retrieveWebProcessesForAppId(String applicationId);

	Mono<ListProcessesResponse> retrieveWebProcessesForAppIds(Set<String> applicationIds);
	
	void reset();
}
