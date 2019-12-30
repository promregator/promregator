package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
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
