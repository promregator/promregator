package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudfoundry.client.v2.domains.Domain;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.routes.Route;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v3.BuildpackData;
import org.cloudfoundry.client.v3.Lifecycle;
import org.cloudfoundry.client.v3.LifecycleType;
import org.cloudfoundry.client.v3.Metadata;
import org.cloudfoundry.client.v3.ToOneRelationship;
import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.domains.DomainRelationships;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
import org.junit.jupiter.api.Assertions;

import reactor.core.publisher.Mono;

public class CFAccessorMassMock implements CFAccessor {
	public static final String UNITTEST_ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP_UUID_PREFIX = "55820b2c-2fa5-11e8-b467-";
	public static final String UNITTEST_SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SHARED_DOMAIN = "shared.domain.example.org";
	public static final String UNITTEST_ROUTE_UUID = "b79d2d45-6f6c-4f9e-bd5b-1a7b3ccac247";
	
	public static final String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public static final String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	
	private Random randomGen = new Random();
	
	private int amountInstances;
	
	public CFAccessorMassMock(int amountInstances) {
		super();
		this.amountInstances = amountInstances;
	}

	private Duration getSleepRandomDuration() {
		return Duration.ofMillis(this.randomGen.nextInt(250));
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		if (spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<SpaceApplicationSummary> list = new LinkedList<>();
					
			for (int i = 0;i<100;i++) {
				Domain sharedDomain = Domain.builder().id(UNITTEST_SHARED_DOMAIN_UUID+i).name(UNITTEST_SHARED_DOMAIN).build();
				final String[] urls = { "hostapp"+i+"."+UNITTEST_SHARED_DOMAIN }; 
				final Route[] routes = { Route.builder().domain(sharedDomain).host("hostapp"+i).build() };
				SpaceApplicationSummary sas = SpaceApplicationSummary.builder()
						.id(UNITTEST_APP_UUID_PREFIX+i)
						.name("testapp"+i)
						.addAllUrls(Arrays.asList(urls))
						.addAllRoutes(Arrays.asList(routes))
						.instances(this.amountInstances)
						.build();
				list.add(sas);
			}
			
			GetSpaceSummaryResponse resp = GetSpaceSummaryResponse.builder().addAllApplications(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		Assertions.fail("Invalid retrieveSpaceSummary request");
		return null;
	}

	@Override
	public Mono<GetInfoResponse> getInfo() {
		GetInfoResponse data = GetInfoResponse.builder()
				.description("CFAccessorMassMock")
				.name("CFAccessorMassMock")
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
		if ("unittestorg".equals(orgName)) {
			org.cloudfoundry.client.v3.organizations.OrganizationResource or = org.cloudfoundry.client.v3.organizations.OrganizationResource.builder()
					.name(orgName)
					.id(UNITTEST_ORG_UUID)
					.createdAt(CREATED_AT_TIMESTAMP)
					.metadata(Metadata.builder().build())
					.build();
			
			List<org.cloudfoundry.client.v3.organizations.OrganizationResource> list = new LinkedList<>();
			list.add(or);
			
			org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse resp = org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
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
		if ("unittestspace".equals(spaceName) && orgId.equals(UNITTEST_ORG_UUID)) {
			
			org.cloudfoundry.client.v3.spaces.SpaceResource sr = org.cloudfoundry.client.v3.spaces.SpaceResource.builder()
					.name(spaceName)
					.createdAt(CREATED_AT_TIMESTAMP)
					.id(UNITTEST_SPACE_UUID)
					.metadata(Metadata.builder().build())
					.build();
			List<org.cloudfoundry.client.v3.spaces.SpaceResource> list = new LinkedList<>();
			list.add(sr);
			org.cloudfoundry.client.v3.spaces.ListSpacesResponse resp = org.cloudfoundry.client.v3.spaces.ListSpacesResponse.builder().addAllResources(list).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		Assertions.fail("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		return this.retrieveSpaceIdV3(orgId, "unittestspace");
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
		if (orgId.equals(UNITTEST_ORG_UUID) && spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<ApplicationResource> list = new LinkedList<>();

			
			for (int i = 0;i<100;i++) {
				ApplicationResource ar = null;
				ar = ApplicationResource.builder()
							.name("testapp"+i)
							.createdAt(CREATED_AT_TIMESTAMP)
							.id(UNITTEST_APP_UUID_PREFIX+i)
							.metadata(Metadata.builder().build())
							.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
							.build();
			
				list.add(ar);
				
			}
			ListApplicationsResponse resp = ListApplicationsResponse.builder().addAllResources(list).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		Assertions.fail("Invalid retrieveAllApplicationIdsInSpace request");
		return null;
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		List<DomainResource> domains = new ArrayList<DomainResource>();

		for (int i = 0;i<100;i++) {
			
			DomainResource domain = DomainResource.builder()
					.name(UNITTEST_SHARED_DOMAIN)
					.isInternal(false)
					.id(UNITTEST_SHARED_DOMAIN_UUID+i)
					.createdAt(CREATED_AT_TIMESTAMP)
					.metadata(Metadata.builder().build())
					.relationships(DomainRelationships.builder().organization(
							ToOneRelationship.builder().build()
					).build())
					.build();

			domains.add(domain);
		}

		ListOrganizationDomainsResponse response = ListOrganizationDomainsResponse.builder().addAllResources(domains).build();
		return Mono.just(response);
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId) {
		throw new UnsupportedOperationException();
	}
}
