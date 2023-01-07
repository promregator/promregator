package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
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
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessRelationships;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
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
	public static final String UNITTEST_ROUTE_UUID = "b79d2d45-6f6c-4f9e-bd5b-1a7b3ccac247";
	
	public static final String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public static final String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public static final String APP_HOST_PREFIX = "hostapp";
	public static final String UNITTEST_ROUTE_UUID_PREFIX = "45fe26fc-2491-495f-966d-aed00c094e54";
	public static final String UNITTEST_PROCESS_UUID_PREFIX = "b7f7eb34-58f1-4f70-aee3-e79124078796";
	
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
							.state(ApplicationState.STARTED)
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

	private RouteResource determineRoutesDataForApp(String appId) {
		final String appNumber = appId.substring(UNITTEST_APP_UUID_PREFIX.length());
		
		ToOneRelationship domain = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_SHARED_DOMAIN_UUID+appNumber).build()).build();
		ToOneRelationship space = ToOneRelationship.builder().data(Relationship.builder().id(UNITTEST_SPACE_UUID).build()).build();
		RouteRelationships rels = RouteRelationships.builder().domain(domain).space(space).build();
		RouteResource rr = RouteResource.builder()
				.url(APP_HOST_PREFIX+appNumber+"."+UNITTEST_SHARED_DOMAIN)
				.relationships(rels)
				.createdAt(CREATED_AT_TIMESTAMP)
				.id(UNITTEST_ROUTE_UUID_PREFIX+appNumber)
				.host(APP_HOST_PREFIX+appNumber)
				.path("/")
				.build();
		return rr;
	}
	
	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId) {
		if (appId.startsWith(UNITTEST_APP_UUID_PREFIX)) {
			RouteResource rr = determineRoutesDataForApp(appId);
			ListRoutesResponse resp = ListRoutesResponse.builder().resource(rr).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		Assertions.fail("Invalid retrieveRoutesForAppId request");
		return null;

	}

	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds) {
		if (appIds == null) {
			return null;
		}
		
		List<RouteResource> list = appIds.stream()
				.map(this::determineRoutesDataForApp)
				.filter(e -> e != null)
				.toList();
		
		ListRoutesResponse resp = ListRoutesResponse.builder().resources(list).build();
		return Mono.just(resp).delayElement(this.getSleepRandomDuration());
	}

	private ProcessResource determineWebProcessesDataForApp(String applicationId) {
		final String appNumber = applicationId.substring(UNITTEST_APP_UUID_PREFIX.length());
		final ProcessResource prWeb = ProcessResource.builder()
				.instances(this.amountInstances)
				.type("web")
				.createdAt(CREATED_AT_TIMESTAMP)
				.command("dummycommand")
				.diskInMb(1024)
				.healthCheck(HealthCheck.builder().type(HealthCheckType.HTTP).build())
				.memoryInMb(1024)
				.metadata(Metadata.builder().build())
				.relationships(ProcessRelationships.builder().build())
				.id(UNITTEST_PROCESS_UUID_PREFIX + appNumber)
				.build();
		return prWeb;
	}
	
	@Override
	public Mono<ListApplicationProcessesResponse> retrieveWebProcessesForAppId(String applicationId) {
		if (applicationId.startsWith(UNITTEST_APP_UUID_PREFIX)) {
			final ProcessResource prWeb = determineWebProcessesDataForApp(applicationId);
			
			ListApplicationProcessesResponse resp = ListApplicationProcessesResponse.builder().resource(prWeb).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		Assertions.fail("Invalid retrieveWebProcessesForApp request");
		return null;
	}

	@Override
	public Mono<ListProcessesResponse> retrieveWebProcessesForAppIds(Set<String> applicationIds) {
		if (applicationIds == null) {
			return null;
		}
		
		List<ProcessResource> list = applicationIds.stream()
				.map(this::determineWebProcessesDataForApp)
				.filter(e -> e != null)
				.toList();
		
		ListProcessesResponse resp = ListProcessesResponse.builder().resources(list).build();
		return Mono.just(resp).delayElement(this.getSleepRandomDuration());
	}
	
}

