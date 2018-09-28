package org.cloudfoundry.promregator.cfaccessor;

import java.time.Instant;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.events.ListEventsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;

import reactor.core.publisher.Mono;

public interface CFAccessor {

	Mono<ListOrganizationsResponse> retrieveOrgId(String orgName);
	
	Mono<ListOrganizationsResponse> retrieveAllOrgIds();

	Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName);
	
	Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId);

	Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId);

	Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId);
	
	Mono<ListEventsResponse> retrieveEvents(Instant sinceTimestamp);
}