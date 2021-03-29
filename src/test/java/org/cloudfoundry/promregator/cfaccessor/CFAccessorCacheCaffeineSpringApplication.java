package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

@Configuration
public class CFAccessorCacheCaffeineSpringApplication {

	@Bean
	public InternalMetrics internalMetrics() {
		return Mockito.mock(InternalMetrics.class);
	}
	
	public static class ParentMock implements CFAccessor {

		@Override
		public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
			return Mono.just( ListOrganizationsResponse.builder().build() );
		}

		@Override
		public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
			return Mono.just(ListOrganizationsResponse.builder().build() );
		}

		@Override
		public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
			return Mono.just(ListSpacesResponse.builder().build());
		}

		@Override
		public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
			return Mono.just(ListSpacesResponse.builder().build());
		}

		@Override
		public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
			return Mono.just(ListApplicationsResponse.builder().build());
		}

		@Override
		public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
			return Mono.just(GetSpaceSummaryResponse.builder().build());
		}

		@Override
		public Mono<GetInfoResponse> getInfo() {
			return Mono.just(GetInfoResponse.builder().build());
		}

		@Override
		public Mono<ListOrganizationDomainsResponse> retrieveAllDomains(String orgId) {
			return Mono.just(ListOrganizationDomainsResponse.builder().build());
		}

		@Override
		public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
			return Mono.just(org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse.builder().build());
		}

		@Override
		public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3() {
			return Mono.just(org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse.builder().build());
		}

		@Override
		public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
			return Mono.just(org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder().build());
		}

		@Override
		public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
			return Mono.just(org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder().build());
		}

		@Override
		public Mono<org.cloudfoundry.client.v3.applications.ListApplicationsResponse> retrieveAllApplicationIdsInSpaceV3(String orgId, String spaceId) {
			return Mono.just(org.cloudfoundry.client.v3.applications.ListApplicationsResponse.builder().build());
		}

		@Override
		public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
			return Mono.just(GetSpaceResponse.builder().id(spaceId).name("dummy").createdAt("time").build());
		}

		@Override
		public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
			return Mono.just(org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse.builder().build());
		}

		@Override
		public Mono<ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId) {
			return Mono.just(ListApplicationRoutesResponse.builder().build());
		}

		@Override
		public void reset() {
			// nothing to be done
		}		
	}
	
	@Bean
	public CFAccessor parentMock() {
		return Mockito.spy(new ParentMock());
	}
	
	@Bean
	public CFAccessorCacheCaffeine subject(@Qualifier("parentMock") CFAccessor parentMock) {
		return new CFAccessorCacheCaffeine(parentMock);
	}
	
}
