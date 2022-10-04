package org.cloudfoundry.promregator.cfaccessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.assertj.core.util.Lists;
import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.domains.Domain;
import org.cloudfoundry.client.v2.domains.DomainEntity;
import org.cloudfoundry.client.v2.domains.DomainResource;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.routes.Route;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v3.BuildpackData;
import org.cloudfoundry.client.v3.Lifecycle;
import org.cloudfoundry.client.v3.LifecycleType;
import org.cloudfoundry.client.v3.applications.ApplicationState;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
import org.junit.jupiter.api.Assertions;

import reactor.core.publisher.Mono;

public class CFAccessorMockV3 implements CFAccessor {
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
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		Assertions.fail("retrieveOrgId (V2) must not be called in V3 tests");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		Assertions.fail("retrieveSpaceId (V2) must not be called in V3 tests");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		Assertions.fail("retrieveAllApplicationIdsinSpace (V2) must not be called in V3 tests");
		throw new UnsupportedOperationException();
		
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		Assertions.fail("retrieveSpaceSummary (V2) must not be called in V3 tests");
		throw new UnsupportedOperationException();
	}

	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		Assertions.fail("retrieveAllOrgIds (V2) must not be called in V3 tests");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		Assertions.fail("retrieveSpaceIdsInOrg (V2) must not be called in V3 tests");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<GetInfoResponse> getInfo() {
		GetInfoResponse data = GetInfoResponse.builder().description("CFAccessorMock").name("CFAccessorMock").version(1)
											  .build();

		return Mono.just(data);
	}

	@Override
	public void reset() {
		// nothing to be done
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomains(String orgId) {
		Assertions.fail("retrieveAllDomains (V2) must not be called in V3 tests");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		if ("unittestorg".equalsIgnoreCase(orgName)) {

			org.cloudfoundry.client.v3.organizations.OrganizationResource or = org.cloudfoundry.client.v3.organizations.OrganizationResource.builder()
																																			.name("unittestorg")
																																			.createdAt(CREATED_AT_TIMESTAMP)
																																			.id(UNITTEST_ORG_UUID)
																																			// Note that UpdatedAt is not set here, as this can also happen in real life!
																																			.build();

			List<org.cloudfoundry.client.v3.organizations.OrganizationResource> list = new LinkedList<>();
			list.add(or);

			org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse resp = org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse
				.builder().addAllResources(list).build();

			return Mono.just(resp);
		}
		else if ("doesnotexist".equals(orgName)) {
			return Mono.just(org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse.builder()
																							   .resources(new ArrayList<>()).build());
		}
		else if ("exception".equals(orgName)) {
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
				org.cloudfoundry.client.v3.spaces.SpaceResource sr = org.cloudfoundry.client.v3.spaces.SpaceResource.builder().name("unittestspace")
																													.createdAt(CREATED_AT_TIMESTAMP)
																													.id(UNITTEST_SPACE_UUID)
																													.build();
				List<org.cloudfoundry.client.v3.spaces.SpaceResource> list = new LinkedList<>();
				list.add(sr);
				org.cloudfoundry.client.v3.spaces.ListSpacesResponse resp = org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder()
																																.addAllResources(list)
																																.build();
				return Mono.just(resp);
			}
			else if ("unittestspace-summarydoesnotexist".equals(spaceName)) {
				org.cloudfoundry.client.v3.spaces.SpaceResource sr = org.cloudfoundry.client.v3.spaces.SpaceResource.builder().name(spaceName)
																													.createdAt(CREATED_AT_TIMESTAMP)
																													.id(UNITTEST_SPACE_UUID_DOESNOTEXIST)
																													.build();
				List<org.cloudfoundry.client.v3.spaces.SpaceResource> list = new LinkedList<>();
				list.add(sr);
				org.cloudfoundry.client.v3.spaces.ListSpacesResponse resp = org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder()
																																.addAllResources(list)
																																.build();
				return Mono.just(resp);
			}
			else if ("unittestspace-summaryexception".equals(spaceName)) {
				org.cloudfoundry.client.v3.spaces.SpaceResource sr = org.cloudfoundry.client.v3.spaces.SpaceResource.builder().name(spaceName)
																													.createdAt(CREATED_AT_TIMESTAMP)
																													.id(UNITTEST_SPACE_UUID_EXCEPTION)
																													.build();
				List<org.cloudfoundry.client.v3.spaces.SpaceResource> list = new LinkedList<>();
				list.add(sr);
				org.cloudfoundry.client.v3.spaces.ListSpacesResponse resp = org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder()
																																.addAllResources(list)
																																.build();
				return Mono.just(resp);
			}
			else if ("doesnotexist".equals(spaceName)) {
				return Mono.just(org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder().resources(new ArrayList<>()).build());
			}
			else if ("exception".equals(spaceName)) {
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
	public Mono<org.cloudfoundry.client.v3.applications.ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
		if (orgId.equals(UNITTEST_ORG_UUID) && spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<org.cloudfoundry.client.v3.applications.ApplicationResource> list = new LinkedList<>();

			org.cloudfoundry.client.v3.applications.ApplicationResource ar = org.cloudfoundry.client.v3.applications.ApplicationResource.builder()
																																		.name("testapp")
																																		.state(ApplicationState.STARTED)
																																		.createdAt(CREATED_AT_TIMESTAMP)
																																		.id(UNITTEST_APP1_UUID)
																																		.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
																																		.metadata(org.cloudfoundry.client.v3.Metadata.builder().annotation("prometheus.io/scrape", "true").build())
																																		.build();
			list.add(ar);

			ar = org.cloudfoundry.client.v3.applications.ApplicationResource.builder()
																			.name("testapp2").state(ApplicationState.STARTED)
																			.createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP2_UUID)
																			.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
																			.metadata(org.cloudfoundry.client.v3.Metadata.builder().annotation("prometheus.io/scrape", "badValue").build())
																			.build();
			list.add(ar);

			ar = org.cloudfoundry.client.v3.applications.ApplicationResource.builder()
					.name("testapp3").state(ApplicationState.STARTED)
					.createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP3_UUID)
					.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
					.metadata(org.cloudfoundry.client.v3.Metadata.builder().annotation("prometheus.io/scrape", "badValue").build())
					.build();
			list.add(ar);
			
			ar = org.cloudfoundry.client.v3.applications.ApplicationResource.builder()
																			.name("internalapp").state(ApplicationState.STARTED)
																			.createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP_INTERNAL_UUID)
																			.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
																			.metadata(org.cloudfoundry.client.v3.Metadata.builder().annotation("prometheus.io/scrape", "true").annotation("prometheus.io/path", "/actuator/prometheus").build())
																			.build();
			list.add(ar);

			org.cloudfoundry.client.v3.applications.ListApplicationsResponse resp = org.cloudfoundry.client.v3.applications.ListApplicationsResponse
				.builder().addAllResources(list).build();
			return Mono.just(resp);
		}
		else if (UNITTEST_SPACE_UUID_DOESNOTEXIST.equals(spaceId)) {
			return Mono.just(org.cloudfoundry.client.v3.applications.ListApplicationsResponse.builder().build());
		}
		else if (UNITTEST_SPACE_UUID_EXCEPTION.equals(spaceId)) {
			return Mono.just(org.cloudfoundry.client.v3.applications.ListApplicationsResponse.builder().build()).map(x -> {
				throw new Error("exception on AllAppIdsInSpace");
			});
		}

		Assertions.fail("Invalid process request");
		return null;
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
		// This API has drastically changed in v3 and does not support the same resources. This call for a space summary will probably
		// take another call to list applications for a space, list routes for the apps, and list domains in the org
		// Previously they were all grouped into this API
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		List<org.cloudfoundry.client.v3.domains.DomainResource> domains = new ArrayList<>();
		org.cloudfoundry.client.v3.domains.DomainResource domain = org.cloudfoundry.client.v3.domains.DomainResource.builder()
																													.name(UNITTEST_INTERNAL_DOMAIN)
																													.isInternal(true)
																													.id(UNITTEST_INTERNAL_DOMAIN_UUID)
																													.createdAt(CREATED_AT_TIMESTAMP)
																													.build();

		domains.add(domain);

		domain = org.cloudfoundry.client.v3.domains.DomainResource.builder()
																  .name(UNITTEST_SHARED_DOMAIN)
																  .isInternal(false)
																  .id(UNITTEST_SHARED_DOMAIN_UUID).createdAt(CREATED_AT_TIMESTAMP)
																  .build();

		domains.add(domain);

		domain = org.cloudfoundry.client.v3.domains.DomainResource.builder()
																  .name(UNITTEST_ADDITIONAL_SHARED_DOMAIN)
																  .isInternal(false)
																  .id(UNITTEST_ADDITIONAL_SHARED_DOMAIN_UUID).createdAt(CREATED_AT_TIMESTAMP)
																  .build();

		domains.add(domain);

		org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse response = org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse
			.builder().addAllResources(domains).build();
		return Mono.just(response);
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isV3Enabled() {
		return true;
	}

}
