package org.cloudfoundry.promregator.cfaccessor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v3.BuildpackData;
import org.cloudfoundry.client.v3.Lifecycle;
import org.cloudfoundry.client.v3.LifecycleType;
import org.cloudfoundry.client.v3.Metadata;
import org.cloudfoundry.client.v3.Relationship;
import org.cloudfoundry.client.v3.ToOneRelationship;
import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ApplicationState;
import org.cloudfoundry.client.v3.applications.ListApplicationProcessesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.domains.DomainRelationships;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.processes.HealthCheck;
import org.cloudfoundry.client.v3.processes.HealthCheckType;
import org.cloudfoundry.client.v3.processes.ProcessRelationships;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.routes.RouteRelationships;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.junit.jupiter.api.Assertions;

import reactor.core.publisher.Mono;

public class CFAccessorMock implements CFAccessor {
	public static final String UNITTEST_ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID_DOESNOTEXIST = "db08be9a-2fa4-11e8-b467-0ed5f89f718b-doesnotexist";
	public static final String UNITTEST_SPACE_UUID_EXCEPTION = "db08be9a-2fa4-11e8-b467-0ed5f89f718b-exception";
	public static final String UNITTEST_APP1_UUID = "55820b2c-2fa5-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP2_UUID = "5a0ead6c-2fa5-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP3_UUID = "4b0ead6c-2fa5-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP1_ROUTE_UUID = "57ac2ada-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP2_ROUTE_UUID = "5c5b464c-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP2_ADDITIONAL_ROUTE_UUID = "efa7710e-c8e2-42c7-ac50-b0a7837c517a";
	public static final String UNITTEST_APP1_HOST = "hostapp1";
	public static final String UNITTEST_APP2_HOST = "hostapp2";
	public static final String UNITTEST_SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SHARED_DOMAIN = "shared.domain.example.org";
	public static final String UNITTEST_ADDITIONAL_SHARED_DOMAIN_UUID = "48ae5bb3-a625-4c16-9e30-e9a6b10ca1be";
	public static final String UNITTEST_ADDITIONAL_SHARED_DOMAIN = "additionalSubdomain.shared.domain.example.org";

	public static final String UNITTEST_INTERNAL_DOMAIN_UUID = "49225c7e-b4c3-45b2-b796-7bb9c64dc79d";
	public static final String UNITTEST_INTERNAL_DOMAIN = "apps.internal";
	public static final String UNITTEST_INTERNAL_ROUTE_UUID = "d1aac229-cc4a-4332-89a7-2efa2378000a";

	public static final String UNITTEST_APP_INTERNAL_UUID = "a8762694-95ce-4c3c-a4fb-250e28187a0a";
	public static final String UNITTEST_APP_INTERNAL_HOST = "internal-app";

	public static final String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public static final String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";

	@Override
	public Mono<GetInfoResponse> getInfo() {
		GetInfoResponse data = GetInfoResponse.builder()
				.description("CFAccessorMock")
				.name("CFAccessorMock")
				.version(1)
				.build();

		return Mono.just(data);
	}

	@Override
	public void reset() {
		// nothing to be done
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		if ("unittestorg".equalsIgnoreCase(orgName)) {

			org.cloudfoundry.client.v3.organizations.OrganizationResource or = org.cloudfoundry.client.v3.organizations.OrganizationResource
					.builder().name("unittestorg").createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_ORG_UUID)
					.metadata(Metadata.builder().build())
					// Note that UpdatedAt is not set here, as this can also happen in real life!
					.build();

			List<org.cloudfoundry.client.v3.organizations.OrganizationResource> list = new LinkedList<>();
			list.add(or);

			org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse resp = org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse
					.builder().addAllResources(list).build();

			return Mono.just(resp);
		} else if ("doesnotexist".equals(orgName)) {
			return Mono.just(org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse.builder()
					.resources(new ArrayList<>()).build());
		} else if ("exception".equals(orgName)) {
			return Mono.just(org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse.builder().build())
					.map(x -> {
						throw new Error("exception org name provided");
					});
		}
		Assertions.fail("Invalid OrgId request");
		return null;
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		return this.retrieveOrgIdV3("unittestorg");
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
		if (orgId.equals(UNITTEST_ORG_UUID)) {
			if ("unittestspace".equalsIgnoreCase(spaceName)) {
				org.cloudfoundry.client.v3.spaces.SpaceResource sr = org.cloudfoundry.client.v3.spaces.SpaceResource
						.builder().name("unittestspace").createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_SPACE_UUID)
						.build();
				List<org.cloudfoundry.client.v3.spaces.SpaceResource> list = new LinkedList<>();
				list.add(sr);
				org.cloudfoundry.client.v3.spaces.ListSpacesResponse resp = org.cloudfoundry.client.v3.spaces.ListSpacesResponse
						.builder().addAllResources(list).build();
				return Mono.just(resp);
			} else if ("unittestspace-summarydoesnotexist".equals(spaceName)) {
				org.cloudfoundry.client.v3.spaces.SpaceResource sr = org.cloudfoundry.client.v3.spaces.SpaceResource
						.builder().name(spaceName).createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_SPACE_UUID_DOESNOTEXIST)
						.build();
				List<org.cloudfoundry.client.v3.spaces.SpaceResource> list = new LinkedList<>();
				list.add(sr);
				org.cloudfoundry.client.v3.spaces.ListSpacesResponse resp = org.cloudfoundry.client.v3.spaces.ListSpacesResponse
						.builder().addAllResources(list).build();
				return Mono.just(resp);
			} else if ("unittestspace-summaryexception".equals(spaceName)) {
				org.cloudfoundry.client.v3.spaces.SpaceResource sr = org.cloudfoundry.client.v3.spaces.SpaceResource
						.builder().name(spaceName).createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_SPACE_UUID_EXCEPTION)
						.build();
				List<org.cloudfoundry.client.v3.spaces.SpaceResource> list = new LinkedList<>();
				list.add(sr);
				org.cloudfoundry.client.v3.spaces.ListSpacesResponse resp = org.cloudfoundry.client.v3.spaces.ListSpacesResponse
						.builder().addAllResources(list).build();
				return Mono.just(resp);
			} else if ("doesnotexist".equals(spaceName)) {
				return Mono.just(org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder()
						.resources(new ArrayList<>()).build());
			} else if ("exception".equals(spaceName)) {
				return Mono.just(org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder().build()).map(x -> {
					throw new Error("exception space name provided");
				});
			}
		}

		Assertions.fail("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		return this.retrieveSpaceIdV3(UNITTEST_ORG_UUID, "unittestspace");
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(
			String orgId, String spaceId) {
		if (orgId.equals(UNITTEST_ORG_UUID) && spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<ApplicationResource> list = new LinkedList<>();

			ApplicationResource ar = ApplicationResource
					.builder().name("testapp").state(ApplicationState.STARTED).createdAt(CREATED_AT_TIMESTAMP)
					.id(UNITTEST_APP1_UUID)
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
					.metadata(Metadata.builder().annotation("prometheus.io/scrape", "true").build())
					.build();
			list.add(ar);

			ar = ApplicationResource.builder().name("testapp2")
					.state(ApplicationState.STARTED).createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP2_UUID)
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK)
							.build())
					.metadata(Metadata.builder()
							.annotation("prometheus.io/scrape", "badValue").build())
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
					.build();
			list.add(ar);

			ar = ApplicationResource.builder().name("testapp3")
					.state(ApplicationState.STARTED).createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP3_UUID)
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK)
							.build())
					.metadata(Metadata.builder()
							.annotation("prometheus.io/scrape", "badValue").build())
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
					.build();
			list.add(ar);

			ar = ApplicationResource.builder().name("internalapp")
					.state(ApplicationState.STARTED).createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP_INTERNAL_UUID)
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK)
							.build())
					.metadata(Metadata.builder().annotation("prometheus.io/scrape", "true")
							.annotation("prometheus.io/path", "/actuator/prometheus").build())
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
					.build();
			list.add(ar);

			ListApplicationsResponse resp = ListApplicationsResponse
					.builder().addAllResources(list).build();
			return Mono.just(resp);
		} else if (UNITTEST_SPACE_UUID_DOESNOTEXIST.equals(spaceId)) {
			return Mono.just(ListApplicationsResponse.builder().build());
		} else if (UNITTEST_SPACE_UUID_EXCEPTION.equals(spaceId)) {
			return Mono.just(ListApplicationsResponse.builder().build())
					.map(x -> {
						throw new Error("exception on AllAppIdsInSpace");
					});
		}

		Assertions.fail("Invalid all application in space v3 request");
		return null;
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(
			String orgId) {
		List<DomainResource> domains = new ArrayList<>();
		DomainResource domain = DomainResource.builder()
				.name(UNITTEST_INTERNAL_DOMAIN)
				.isInternal(true)
				.id(UNITTEST_INTERNAL_DOMAIN_UUID)
				.createdAt(CREATED_AT_TIMESTAMP)
				.relationships(DomainRelationships.builder().organization(
						ToOneRelationship.builder().build()
				).build())
				.build();

		domains.add(domain);

		domain = DomainResource.builder()
				.name(UNITTEST_SHARED_DOMAIN)
				.isInternal(false)
				.id(UNITTEST_SHARED_DOMAIN_UUID)
				.createdAt(CREATED_AT_TIMESTAMP)
				.relationships(DomainRelationships.builder().organization(
						ToOneRelationship.builder().build()
				).build())
				.build();

		domains.add(domain);

		domain = DomainResource.builder()
				.name(UNITTEST_ADDITIONAL_SHARED_DOMAIN)
				.isInternal(false)
				.id(UNITTEST_ADDITIONAL_SHARED_DOMAIN_UUID)
				.createdAt(CREATED_AT_TIMESTAMP)
				.relationships(DomainRelationships.builder().organization(
						ToOneRelationship.builder().build()
				).build())
				.build();

		domains.add(domain);

		ListOrganizationDomainsResponse response = ListOrganizationDomainsResponse
				.builder().addAllResources(domains).build();
		return Mono.just(response);
	}

	private List<RouteResource> determineRoutesDataForApp(String appId) {
		List<RouteResource> result = new ArrayList<>(2);
		
		if (appId.equals(UNITTEST_APP1_UUID)) {
			ToOneRelationship domain = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_SHARED_DOMAIN_UUID).build()).build();
			ToOneRelationship space = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_SPACE_UUID).build()).build();
			RouteRelationships rels = RouteRelationships.builder().domain(domain).space(space).build();
			RouteResource rr = RouteResource.builder()
					.url(UNITTEST_APP1_HOST+"."+UNITTEST_SHARED_DOMAIN)
					.relationships(rels)
					.createdAt(CREATED_AT_TIMESTAMP)
					.id(UNITTEST_APP1_ROUTE_UUID)
					.host(UNITTEST_APP1_HOST)
					.path("/")
					.build();
			result.add(rr);
		}
		
		if (appId.equals(UNITTEST_APP2_UUID)) {
			ToOneRelationship domain = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_SHARED_DOMAIN_UUID).build()).build();
			ToOneRelationship space = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_SPACE_UUID).build()).build();
			RouteRelationships rels = RouteRelationships.builder().domain(domain).space(space).build();
			RouteResource rr = RouteResource.builder()
					.url(UNITTEST_APP2_HOST+"."+UNITTEST_SHARED_DOMAIN+"/additionalPath")
					.relationships(rels)
					.createdAt(CREATED_AT_TIMESTAMP)
					.id(UNITTEST_APP2_ROUTE_UUID)
					.host(UNITTEST_APP2_HOST)
					.path("/additionalPath")
					.build();
			result.add(rr);
			
			ToOneRelationship domainInternal = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_ADDITIONAL_SHARED_DOMAIN_UUID).build()).build();
			RouteRelationships relsInternal = RouteRelationships.builder().domain(domainInternal).space(space).build();
			rr = RouteResource.builder()
					.url(UNITTEST_APP2_HOST + "." + UNITTEST_ADDITIONAL_SHARED_DOMAIN +"/additionalPath")
					.relationships(relsInternal)
					.createdAt(CREATED_AT_TIMESTAMP)
					.id(UNITTEST_APP2_ROUTE_UUID+"i")
					.host(UNITTEST_APP2_HOST)
					.path("/additionalPath")
					.build();
			
			result.add(rr);
		}
		
		if (appId.equals(UNITTEST_APP3_UUID)) {
			// App3 does not have any route (unit test case)
			return null;
		}
		
		if (appId.equals(UNITTEST_APP_INTERNAL_UUID)) {
			ToOneRelationship domain = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_INTERNAL_DOMAIN_UUID).build()).build();
			ToOneRelationship space = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_SPACE_UUID).build()).build();
			RouteRelationships rels = RouteRelationships.builder().domain(domain).space(space).build();
			RouteResource rr = RouteResource.builder()
					.url(UNITTEST_APP_INTERNAL_HOST+"."+UNITTEST_INTERNAL_DOMAIN)
					.relationships(rels)
					.createdAt(CREATED_AT_TIMESTAMP)
					.id(UNITTEST_INTERNAL_ROUTE_UUID)
					.host(UNITTEST_APP_INTERNAL_HOST)
					.path("/")
					.build();
			result.add(rr);
		}
		
		return result;
	}
	
	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId) {
		List<RouteResource> list = this.determineRoutesDataForApp(appId);
		
		if (list == null) {
			return Mono.just(ListRoutesResponse.builder().build());
		}
		ListRoutesResponse resp = ListRoutesResponse.builder().resources(list).build();
		return Mono.just(resp);
	}

	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds) {
		if (appIds == null) {
			return null;
		}
		
		List<RouteResource> list = appIds.stream()
				.flatMap(appId -> this.determineRoutesDataForApp(appId).stream())
				.filter(e -> e != null)
				.toList();
		
		ListRoutesResponse resp = ListRoutesResponse.builder().resources(list).build();
		return Mono.just(resp);
	}

	@Override
	public Mono<ListApplicationProcessesResponse> retrieveWebProcessesForApp(String applicationId) {
		if (applicationId.equals(UNITTEST_APP1_UUID) || applicationId.equals(UNITTEST_APP2_UUID) || applicationId.equals(UNITTEST_APP3_UUID) || applicationId.equals(UNITTEST_APP_INTERNAL_UUID)) {
			final ProcessResource prWeb = ProcessResource.builder()
					.instances(applicationId.equals(UNITTEST_APP1_UUID) || applicationId.equals(UNITTEST_APP_INTERNAL_UUID) ? 2 : 1)
					.type("web")
					.createdAt(CREATED_AT_TIMESTAMP)
					.command("dummycommand")
					.diskInMb(1024)
					.healthCheck(HealthCheck.builder().type(HealthCheckType.HTTP).build())
					.memoryInMb(1024)
					.metadata(Metadata.builder().build())
					.relationships(ProcessRelationships.builder().build())
					.id(applicationId+"p")
					.build();
			
			ListApplicationProcessesResponse resp = ListApplicationProcessesResponse.builder().resource(prWeb).build();
			return Mono.just(resp);
		}
		Assertions.fail("Invalid process request");
		return null;
	}

}
