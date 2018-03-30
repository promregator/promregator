package org.cloudfoundry.promregator.cfaccessor;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.applications.ApplicationEntity;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationEntity;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsResponse;
import org.cloudfoundry.client.v2.routemappings.RouteMappingEntity;
import org.cloudfoundry.client.v2.routemappings.RouteMappingResource;
import org.cloudfoundry.client.v2.routes.GetRouteResponse;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainResponse;
import org.cloudfoundry.client.v2.shareddomains.SharedDomainEntity;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceEntity;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.processes.Data;
import org.cloudfoundry.client.v3.processes.HealthCheck;
import org.cloudfoundry.client.v3.processes.HealthCheckType;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.processes.ProcessResource.Builder;
import org.junit.Assert;

import reactor.core.publisher.Mono;

public class CFAccessorMock implements CFAccessor {
	public final static String UNITTEST_ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public final static String UNITTEST_SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public final static String UNITTEST_APP1_UUID = "55820b2c-2fa5-11e8-b467-0ed5f89f718b";
	public final static String UNITTEST_APP2_UUID = "5a0ead6c-2fa5-11e8-b467-0ed5f89f718b";
	public final static String UNITTEST_APP1_ROUTE_UUID = "57ac2ada-2fa6-11e8-b467-0ed5f89f718b";
	public final static String UNITTEST_APP2_ROUTE_UUID = "5c5b464c-2fa6-11e8-b467-0ed5f89f718b";
	public final static String UNITTEST_APP1_HOST = "hostapp1";
	public final static String UNITTEST_APP2_HOST = "hostapp2";
	public final static String UNITTEST_SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
	public final static String UNITTEST_SHARED_DOMAIN = "shared.domain.example.org";
	
	public final static String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public final static String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		
		if ("unittestorg".equals(orgName)) {
			
			OrganizationResource or = OrganizationResource.builder().entity(
					OrganizationEntity.builder().name(orgName).build()
				).metadata(
					Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_ORG_UUID).build()
					// Note that UpdatedAt is not set here, as this can also happen in real life!
				).build();
			
			List<org.cloudfoundry.client.v2.organizations.OrganizationResource> list = new LinkedList<>();
			list.add(or);
			
			ListOrganizationsResponse resp = org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp);
		}
		Assert.fail("Invalid OrgId request");
		return null;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		if ("unittestspace".equals(spaceName) && orgId.equals(UNITTEST_ORG_UUID)) {
			
			SpaceResource sr = SpaceResource.builder().entity(
					SpaceEntity.builder().name(spaceName).build()
				).metadata(
					Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_SPACE_UUID).build()
				).build();
			List<SpaceResource> list = new LinkedList<>();
			list.add(sr);
			ListSpacesResponse resp = ListSpacesResponse.builder().addAllResources(list).build();
			return Mono.just(resp);
		}
		
		Assert.fail("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName) {
		if (orgId.equals(UNITTEST_ORG_UUID) && spaceId.equals(UNITTEST_SPACE_UUID)) {
			ApplicationResource ar = null;
			if (applicationName.equals("testapp")) {
				ar = ApplicationResource.builder().entity(
						ApplicationEntity.builder().name(applicationName).build()
					).metadata(
							Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP1_UUID).build()
					).build();
			} else if (applicationName.equals("testapp2")) {
				ar = ApplicationResource.builder().entity(
						ApplicationEntity.builder().name(applicationName).build()
					).metadata(
							Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP2_UUID).build()
					).build();
			} else {
				Assert.fail("Invalid ApplicationId request, application name is invalid");
			}
			
			List<ApplicationResource> list = new LinkedList<>();
			list.add(ar);
			ListApplicationsResponse resp = ListApplicationsResponse.builder().addAllResources(list).build();
			return Mono.just(resp);
		}
		
		Assert.fail("Invalid ApplicationId request");
		return null;
	}

	@Override
	public Mono<ListRouteMappingsResponse> retrieveRouteMapping(String appId) {
		RouteMappingEntity entity = null;
		if (appId.equals(UNITTEST_APP1_UUID)) {
			entity = RouteMappingEntity.builder().applicationId(appId).routeId(UNITTEST_APP1_ROUTE_UUID).build();
		} else if (appId.equals(UNITTEST_APP2_UUID)) {
			entity = RouteMappingEntity.builder().applicationId(appId).routeId(UNITTEST_APP2_ROUTE_UUID).build();
		}
		
		if (entity == null) {
			Assert.fail("Invalid route mapping request");
			return null;
		}

		RouteMappingResource rmr = null;
		rmr = RouteMappingResource.builder().entity(entity).build();

		List<RouteMappingResource> list = new LinkedList<>();
		list.add(rmr);
		ListRouteMappingsResponse resp = ListRouteMappingsResponse.builder().addAllResources(list).build();
		
		return Mono.just(resp);
	}

	@Override
	public Mono<GetRouteResponse> retrieveRoute(String routeId) {
		
		RouteEntity entity = null;
		if (routeId.equals(UNITTEST_APP1_ROUTE_UUID)) {
			entity = RouteEntity.builder().domainId(UNITTEST_SHARED_DOMAIN_UUID).host(UNITTEST_APP1_HOST).build();
		} else if (routeId.equals(UNITTEST_APP2_ROUTE_UUID)) {
			entity = RouteEntity.builder().domainId(UNITTEST_SHARED_DOMAIN_UUID).host(UNITTEST_APP2_HOST).path("additionalPath").build();
		}
		
		if (entity == null) {
			Assert.fail("Invalid route request");
			return null;
		}
		
		GetRouteResponse resp = GetRouteResponse.builder().entity(entity).build();
		return Mono.just(resp);
	}

	@Override
	public Mono<GetSharedDomainResponse> retrieveSharedDomain(String domainId) {
		
		if (domainId.equals(UNITTEST_SHARED_DOMAIN_UUID)) {
			SharedDomainEntity entity = SharedDomainEntity.builder().name(UNITTEST_SHARED_DOMAIN).build();
			GetSharedDomainResponse resp = GetSharedDomainResponse.builder().entity(entity).build();
			
			return Mono.just(resp);
		}
		
		Assert.fail("Invalid shared domain request");
		return null;
	}

	@Override
	public Mono<ListProcessesResponse> retrieveProcesses(String orgId, String spaceId, String appId) {
		if (orgId.equals(UNITTEST_ORG_UUID) && spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<ProcessResource> list = new LinkedList<>();
			
			Data data = Data.builder().timeout(100).build();
			HealthCheck hc = HealthCheck.builder().type(HealthCheckType.HTTP).data(data).build();
			Builder builder = ProcessResource.builder().type("dummy").command("dummycommand").memoryInMb(1024).diskInMb(1024)
					.healthCheck(hc).createdAt(CREATED_AT_TIMESTAMP).updatedAt(UPDATED_AT_TIMESTAMP);
			ProcessResource ar = null;
			if (appId.equals(UNITTEST_APP1_UUID)) {
				ar = builder.instances(2).id(UNITTEST_APP1_UUID).build();
			} else if (appId.equals(UNITTEST_APP2_UUID)) {
				ar = builder.instances(1).id(UNITTEST_APP2_UUID).build();
			}
			if (ar == null) {
				Assert.fail("Invalid process request, invalid app id provided");
				return null;
			}
			list.add(ar);
			
			ListProcessesResponse resp = ListProcessesResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp);
		}
		
		Assert.fail("Invalid process request");
		return null;
	}

}
