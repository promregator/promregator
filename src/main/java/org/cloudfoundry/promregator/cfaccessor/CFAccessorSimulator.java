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
import org.cloudfoundry.client.v3.ToOneRelationship;
import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ApplicationState;
import org.cloudfoundry.client.v3.applications.ListApplicationProcessesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.domains.DomainRelationships;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.organizations.OrganizationResource;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
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
	public static final String APP_ROUTE_UUID = "676fe9a7-2f41-4b71-8a10-22294af1e81e";  
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
	public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
		/*
		 * 		if (spaceId.equals(SPACE_UUID)) {
			List<SpaceApplicationSummary> list = new LinkedList<>();
			
			for (int i = 1;i<=100;i++) {
				Domain sharedDomain = Domain.builder().id(SHARED_DOMAIN_UUID+i).name(SHARED_DOMAIN).build();
				final String[] urls = { APP_HOST_PREFIX+i+"."+SHARED_DOMAIN }; 
				final Route[] routes = { Route.builder().domain(sharedDomain).host(APP_HOST_PREFIX+i).build() };
				SpaceApplicationSummary sas = SpaceApplicationSummary.builder()
						.id(APP_UUID_PREFIX+i)
						.name("testapp"+i)
						.addAllUrls(Arrays.asList(urls))
						.addAllRoutes(Arrays.asList(routes))
						.instances(this.amountInstances)
						.state("STARTED")
						.build();
				list.add(sas);
			}
			
			GetSpaceSummaryResponse resp = GetSpaceSummaryResponse.builder().addAllApplications(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid retrieveSpaceSummary request");
		return null;
		 */
		/* TODO V3: Requires implementation? */
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		List<DomainResource> domains = new ArrayList<DomainResource>();

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

	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppId(String appId) {
		/* TODO V3: Requires implementation? */
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListRoutesResponse> retrieveRoutesForAppIds(Set<String> appIds) {
		/* TODO V3: Requires implementation? */
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListApplicationProcessesResponse> retrieveWebProcessesForApp(String applicationId) {
		/* TODO V3: Requires implementation? */
		throw new UnsupportedOperationException();
	}
}
