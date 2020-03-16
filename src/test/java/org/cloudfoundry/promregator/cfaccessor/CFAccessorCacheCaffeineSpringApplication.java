package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
		public void reset() {
			// nothing to be done
		}
		
	}
	
	@Bean
	public CFAccessor parentMock() {
		return Mockito.spy(new ParentMock());
	}
	
	@Bean
	public CFAccessorCacheCaffeine subject(@Value("${cf.cache.timeout.org:3600}") int refreshCacheOrgLevelInSeconds,
										   @Value("${cf.cache.timeout.space:3600}") int refreshCacheSpaceLevelInSeconds,
										   @Value("${cf.cache.timeout.application:300}") int refreshCacheApplicationLevelInSeconds,
										   @Value("${cf.cache.expiry.org:120}") int expiryCacheOrgLevelInSeconds,
										   @Value("${cf.cache.expiry.space:120}") int expiryCacheSpaceLevelInSeconds,
										   @Value("${cf.cache.expiry.application:120}") int expiryCacheApplicationLevelInSeconds,
										   InternalMetrics internalMetrics,
										   @Qualifier("parentMock") CFAccessor parentMock) {
		return new CFAccessorCacheCaffeine(refreshCacheOrgLevelInSeconds, refreshCacheSpaceLevelInSeconds, refreshCacheApplicationLevelInSeconds, expiryCacheOrgLevelInSeconds, expiryCacheSpaceLevelInSeconds, expiryCacheApplicationLevelInSeconds, internalMetrics, parentMock);
	}
	
}
