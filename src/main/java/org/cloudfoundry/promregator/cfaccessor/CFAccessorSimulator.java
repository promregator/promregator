package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.domains.DomainRelationships;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.organizations.OrganizationResource;
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
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.spaces.SpaceResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class CFAccessorSimulator implements CFAccessor {
	public static final String ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public static final String SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public static final String APP_UUID_PREFIX = "55820b2c-2fa5-11e8-b467-";
	public static final String APP_HOST_PREFIX = "hostapp";
	public static final String SHARED_DOMAIN = "shared.domain.example.org";
	public static final String SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
	public static final String INTERNAL_DOMAIN = "apps.internal";
	public static final String INTERNAL_DOMAIN_UUID = "61c11947-087b-4894-a64f-4d9d5f619b58";
	public static final String PROCESS_UUID_PREFIX = "8f7815cc-c224-43ae-8956-08d11d278d4c";
	public static final String ROUTE_UUID_PREFIX = "676fe9a7-2f41-4b71-8a10-22294af1e81e";
	public static final String APP_ROUTE_PATH = "path";
	
	private static final Logger log = LoggerFactory.getLogger(CFAccessorSimulator.class);
	
	public static final String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public static final String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	
	private Random randomGen = new Random();
	
	private int amountInstances;
	
	public CFAccessorSimulator(int amountInstances) {
		super();
		this.amountInstances = amountInstances;
	}

	private Duration getSleepRandomDuration() {
		return Duration.ofMillis(this.randomGen.nextInt(250));
	}

	@Override
	public Mono<GetInfoResponse> getInfo() {
		GetInfoResponse data = GetInfoResponse.builder()
				.description("CFSimulator")
				.name("CFSimulator")
				.version(1)
				.build();
		
		return Mono.just(data);
	}
	
	@Override
	public void reset() {
		// nothing to do
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		if ("simorg".equals(orgName)) {
			OrganizationResource or = OrganizationResource.builder()
					.name(orgName)
					.id(ORG_UUID)
					.createdAt(CREATED_AT_TIMESTAMP)
					.metadata(Metadata.builder().build())
					.build();
			
			List<OrganizationResource> list = new LinkedList<>();
			list.add(or);
			
			ListOrganizationsResponse resp = ListOrganizationsResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		log.error("Invalid OrgId request");
		return null;
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		return this.retrieveOrgIdV3("simorg");
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
		if ("simspace".equals(spaceName) && orgId.equals(ORG_UUID)) {
			
			SpaceResource sr = SpaceResource.builder()
					.name(spaceName)
					.createdAt(CREATED_AT_TIMESTAMP)
					.id(SPACE_UUID)
					.metadata(Metadata.builder().build())
					.build();
			List<SpaceResource> list = new LinkedList<>();
			list.add(sr);
			ListSpacesResponse resp = ListSpacesResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		return this.retrieveSpaceIdV3(ORG_UUID, "simspace");
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
		if (orgId.equals(ORG_UUID) && spaceId.equals(SPACE_UUID)) {
			List<ApplicationResource> list = new LinkedList<>();
			
			for (int i = 1;i<=100;i++) {
				ApplicationResource ar = null;
				ar = ApplicationResource.builder()
							.name("testapp"+i)
							.state(ApplicationState.STARTED)
							.createdAt(CREATED_AT_TIMESTAMP)
							.id(APP_UUID_PREFIX+i)
							.metadata(Metadata.builder().build())
							.lifecycle(Lifecycle.builder().data(BuildpackData.builder().build()).type(LifecycleType.BUILDPACK).build())
							.build();
			
				list.add(ar);
				
			}
			ListApplicationsResponse resp = ListApplicationsResponse.builder().addAllResources(list).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		log.error("Invalid retrieveAllApplicationIdsInSpaceV3 request");
		return null;
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		List<DomainResource> domains = new ArrayList<>();

		for (int i = 1;i<=100;i++) {
			
			DomainResource domain = DomainResource.builder()
					.name(SHARED_DOMAIN)
					.isInternal(false)
					.id(SHARED_DOMAIN_UUID+i)
					.createdAt(CREATED_AT_TIMESTAMP)
					.metadata(Metadata.builder().build())
					.relationships(DomainRelationships.builder().organization(
							ToOneRelationship.builder().build()
					).build())
					.build();

			domains.add(domain);
		}

		DomainResource domain = DomainResource.builder()
				.name(INTERNAL_DOMAIN)
				.isInternal(true)
				.id(INTERNAL_DOMAIN_UUID)
				.createdAt(CREATED_AT_TIMESTAMP)
				.metadata(Metadata.builder().build())
				.relationships(DomainRelationships.builder().organization(
						ToOneRelationship.builder().build()
				).build())
				.build();

		domains.add(domain);
		
		ListOrganizationDomainsResponse response = ListOrganizationDomainsResponse.builder().addAllResources(domains).build();
		return Mono.just(response);
	}

	private RouteResource determineRoutesDataForApp(String appId) {
		final String appNumber = appId.substring(APP_UUID_PREFIX.length());
		
		ToOneRelationship domain = ToOneRelationship.builder().data(Relationship.builder().id(SHARED_DOMAIN_UUID+appNumber).build()).build();
		ToOneRelationship space = ToOneRelationship.builder().data(Relationship.builder().id(SPACE_UUID).build()).build();
		RouteRelationships rels = RouteRelationships.builder().domain(domain).space(space).build();
		Destination dest = Destination.builder().destinationId("42"+appId).application(Application.builder().applicationId(appId).build()).build();
		RouteResource rr = RouteResource.builder()
				.url(APP_HOST_PREFIX+appNumber+"."+SHARED_DOMAIN)
				.relationships(rels)
				.createdAt(CREATED_AT_TIMESTAMP)
				.id(ROUTE_UUID_PREFIX+appNumber)
				.host(APP_HOST_PREFIX+appNumber)
				.path(APP_ROUTE_PATH)
				.destination(dest)
				.build();
		return rr;
	}
	
	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId) {
		if (appId.startsWith(APP_UUID_PREFIX)) {
			RouteResource rr = this.determineRoutesDataForApp(appId);
			ListRoutesResponse resp = ListRoutesResponse.builder().resource(rr).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid retrieveRoutesForAppId request");
		return null;
	}

	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds) {
		if (appIds == null) {
			return null;
		}
		
		List<RouteResource> list = appIds.stream()
				.map(this::determineRoutesDataForApp)
				.filter(Objects::nonNull)
				.toList();
		
		ListRoutesResponse resp = ListRoutesResponse.builder().resources(list).build();
		return Mono.just(resp).delayElement(this.getSleepRandomDuration());
	}

	private ProcessResource determineWebProcessesForAppId(String applicationId) {
		final String appNumber = applicationId.substring(APP_UUID_PREFIX.length());
		return ProcessResource.builder()
				.instances(this.amountInstances)
				.type("web")
				.createdAt(CREATED_AT_TIMESTAMP)
				.command("dummycommand")
				.diskInMb(1024)
				.healthCheck(HealthCheck.builder().type(HealthCheckType.HTTP).build())
				.memoryInMb(1024)
				.metadata(Metadata.builder().build())
				.relationships(ProcessRelationships.builder()
						.app(ToOneRelationship.builder().data(Relationship.builder()
								.id(applicationId)
								.build())
							.build())
						.build())
				.id(PROCESS_UUID_PREFIX + appNumber)
				.build();
	}

	
	@Override
	public Mono<ListProcessesResponse> retrieveWebProcessesForAppId(String applicationId) {
		if (applicationId.startsWith(APP_UUID_PREFIX)) {
			final ProcessResource prWeb = this.determineWebProcessesForAppId(applicationId);
			
			ListProcessesResponse resp = ListProcessesResponse.builder().resource(prWeb).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid retrieveWebProcessesForApp request");
		return null;
	}


	@Override
	public Mono<ListProcessesResponse> retrieveWebProcessesForAppIds(Set<String> applicationIds) {
		if (applicationIds == null) {
			return null;
		}
		
		List<ProcessResource> list = applicationIds.stream()
				.map(this::determineWebProcessesForAppId)
				.filter(Objects::nonNull)
				.toList();
		
		ListProcessesResponse resp = ListProcessesResponse.builder().resources(list).build();
		return Mono.just(resp).delayElement(this.getSleepRandomDuration());
	}
}
