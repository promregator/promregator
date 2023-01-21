package org.cloudfoundry.promregator.cfaccessor;

import java.util.List;
import java.util.Set;

import org.cloudfoundry.client.v3.Metadata;
import org.cloudfoundry.client.v3.Relationship;
import org.cloudfoundry.client.v3.ToOneRelationship;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.processes.HealthCheck;
import org.cloudfoundry.client.v3.processes.HealthCheckType;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessRelationships;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.Application;
import org.cloudfoundry.client.v3.routes.Destination;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.routes.RouteRelationships;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.cloudfoundry.promregator.cfaccessor.client.InfoV3;
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
		public Mono<InfoV3> getInfo() {
			return Mono.just(new InfoV3());
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
		public Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
			return Mono.just(ListApplicationsResponse.builder().build());
		}

		@Override
		public Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
			return Mono.just(ListOrganizationDomainsResponse.builder().build());
		}

		@Override
		public Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId) {
			return this.retrieveRoutesForAppIds(Set.of(appId));
		}

		@Override
		public void reset() {
			// nothing to be done
		}

		@Override
		public Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds) {
			List<RouteResource> list = appIds.stream().map(id -> {
				Destination dest = Destination.builder().application(Application.builder().applicationId(id).build()).build();
				RouteRelationships relationships = RouteRelationships.builder()
						.domain(ToOneRelationship.builder().data(Relationship.builder().id("1").build()).build())
						.space(ToOneRelationship.builder().data(Relationship.builder().id("2").build()).build())
						.build();
				RouteResource rr = RouteResource.builder().id(id).destination(dest).host("dummy.bogus").path("/").createdAt("something").url("http://dummy.bogus/").relationships(relationships).build();
				return rr;
			}).toList();
			
			return Mono.just(ListRoutesResponse.builder().resources(list).build());
		}

		@Override
		public Mono<ListProcessesResponse> retrieveWebProcessesForAppId(String applicationId) {
			return this.retrieveWebProcessesForAppIds(Set.of(applicationId));
		}

		@Override
		public Mono<ListProcessesResponse> retrieveWebProcessesForAppIds(Set<String> applicationIds) {
			
			
			List<ProcessResource> list = applicationIds.stream().map(id -> {
				ProcessRelationships relationships = ProcessRelationships.builder().app(ToOneRelationship.builder().data(Relationship.builder().id(id).build()).build()).build();
				ProcessResource pr = ProcessResource.builder()
						.id(id)
						.diskInMb(1024)
						.createdAt("something")
						.memoryInMb(256)
						.instances(1)
						.type("web")
						.relationships(relationships)
						.command("cmd")
						.healthCheck(HealthCheck.builder().type(HealthCheckType.HTTP).build())
						.metadata(Metadata.builder().build())
						.build();
				return pr;
			}).toList();
			return Mono.just(ListProcessesResponse.builder().resources(list).build());
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
