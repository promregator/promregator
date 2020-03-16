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
import org.junit.Assert;

import reactor.core.publisher.Mono;

public class CFAccessorMassMock implements CFAccessor {
	public static final String UNITTEST_ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP_UUID_PREFIX = "55820b2c-2fa5-11e8-b467-";
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
	public Mono<ListOrganizationsResponse> retrieveOrgId(String api, String orgName) {
		
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
		Assert.fail("Invalid OrgId request");
		return null;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String api, String orgId, String spaceName) {
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
		
		Assert.fail("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String api, String orgId, String spaceId) {
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
		Assert.fail("Invalid retrieveAllApplicationIdsInSpace request");
		return null;
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String api, String spaceId) {
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
		
		Assert.fail("Invalid retrieveSpaceSummary request");
		return null;
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds(String api) {
		return this.retrieveOrgId(api, "unittestorg");
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String api, String orgId) {
		return this.retrieveSpaceId(api, UNITTEST_ORG_UUID, "unittestspace");
	}

	@Override
	public Mono<GetInfoResponse> getInfo(String api) {
		GetInfoResponse data = GetInfoResponse.builder()
				.description("CFAccessorMassMock")
				.name("CFAccessorMassMock")
				.version(1)
				.build();
		
		return Mono.just(data);
	}

}
