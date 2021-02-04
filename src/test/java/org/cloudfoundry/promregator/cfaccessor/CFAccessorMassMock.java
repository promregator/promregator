package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.applications.ApplicationEntity;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationEntity;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v2.spaces.SpaceEntity;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.Relationship;
import org.cloudfoundry.client.v3.ToOneRelationship;
import org.cloudfoundry.client.v3.routes.Application;
import org.cloudfoundry.client.v3.routes.Destination;
import org.cloudfoundry.client.v3.routes.Process;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.domains.DomainRelationships;
import org.cloudfoundry.client.v3.domains.GetDomainResponse;
import org.cloudfoundry.client.v3.routes.RouteRelationships;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.junit.jupiter.api.Assertions;

import reactor.core.publisher.Mono;

public class CFAccessorMassMock implements CFAccessor {
	public static final String UNITTEST_ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP_UUID_PREFIX = "55820b2c-2fa5-11e8-b467-";
	public static final String UNITTEST_SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SHARED_DOMAIN = "shared.domain.example.org";
	
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
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		Assertions.fail("Invalid OrgId request");
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
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		Assertions.fail("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		if (orgId.equals(UNITTEST_ORG_UUID) && spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<ApplicationResource> list = new LinkedList<>();

			
			for (int i = 0;i<100;i++) {
				ApplicationResource ar = null;
				ar = ApplicationResource.builder().entity(
						ApplicationEntity.builder().name("testapp"+i).build()
					).metadata(
							Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP_UUID_PREFIX+i).build()
					).build();
			
				list.add(ar);
				
			}
			ListApplicationsResponse resp = ListApplicationsResponse.builder().addAllResources(list).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		Assertions.fail("Invalid retrieveAllApplicationIdsInSpace request");
		return null;
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		if (spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<SpaceApplicationSummary> list = new LinkedList<>();
			
			for (int i = 0;i<100;i++) {
				final String[] urls = { "hostapp"+i+"."+UNITTEST_SHARED_DOMAIN }; 
				SpaceApplicationSummary sas = SpaceApplicationSummary.builder()
						.id(UNITTEST_APP_UUID_PREFIX+i)
						.name("testapp"+i)
						.addAllUrls(Arrays.asList(urls))
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

	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		return this.retrieveOrgId("unittestorg");
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		return this.retrieveSpaceId(UNITTEST_ORG_UUID, "unittestspace");
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
	public Mono<GetDomainResponse> retrieveDomain(String domainId) {
		GetDomainResponse response = GetDomainResponse.builder()
				.createdAt(CREATED_AT_TIMESTAMP) 
				.id(domainId)
				.name(UNITTEST_SHARED_DOMAIN) 
				.relationships(
					DomainRelationships.builder()
					.organization(
					ToOneRelationship.builder()
					.data(
						Relationship.builder().id(UNITTEST_ORG_UUID).build()
					).build()
					).build()
				)  
				.isInternal(false)
				.build();
		
		return Mono.just(response);
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveAppRoutes(String appId) {
		List<RouteResource> routes = new LinkedList<>();

		String instanceId = appId.replace(UNITTEST_APP_UUID_PREFIX, "");

		RouteResource res = RouteResource.builder()
				.id("id")
				.createdAt(CREATED_AT_TIMESTAMP)
				.host("hostapp"+instanceId)    
				.path("path")
				.url("hostapp" + instanceId + "." + UNITTEST_SHARED_DOMAIN)
				.relationships(
					RouteRelationships.builder()
						.domain(
							ToOneRelationship.builder().data(
								Relationship.builder().id(UNITTEST_SHARED_DOMAIN_UUID).build()
								).build()
						)
						.space(
							ToOneRelationship.builder().data(
								Relationship.builder().id(UNITTEST_SPACE_UUID).build()
								).build()
						).build())      
				.destination(
					Destination.builder()
						.port(8080)
						.application(
							Application.builder()
								.applicationId(appId)
								.process(
									Process.builder().type("web").build()
								).build())
						.build()
				).build();
			
		routes.add(res);

		ListApplicationRoutesResponse resp = ListApplicationRoutesResponse.builder().addAllResources(routes).build();
		return Mono.just(resp);
	}
}
