package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.applications.ApplicationEntity;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationEntity;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.routes.Route;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v2.spaces.SpaceEntity;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v2.domains.Domain;
import org.cloudfoundry.client.v2.domains.DomainEntity;
import org.cloudfoundry.client.v2.domains.DomainResource;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
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
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		
		if ("simorg".equals(orgName)) {
			
			OrganizationResource or = OrganizationResource.builder().entity(
					OrganizationEntity.builder().name(orgName).build()
				).metadata(
					Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(ORG_UUID).build()
					// Note that UpdatedAt is not set here, as this can also happen in real life!
				).build();
			
			List<OrganizationResource> list = new LinkedList<>();
			list.add(or);
			
			ListOrganizationsResponse resp = ListOrganizationsResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		log.error("Invalid OrgId request");
		return null;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		if ("simspace".equals(spaceName) && orgId.equals(ORG_UUID)) {
			
			SpaceResource sr = SpaceResource.builder().entity(
					SpaceEntity.builder().name(spaceName).build()
				).metadata(
					Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(SPACE_UUID).build()
				).build();
			List<SpaceResource> list = new LinkedList<>();
			list.add(sr);
			ListSpacesResponse resp = ListSpacesResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		if (orgId.equals(ORG_UUID) && spaceId.equals(SPACE_UUID)) {
			List<ApplicationResource> list = new LinkedList<>();

			
			for (int i = 1;i<=100;i++) {
				ApplicationResource ar = null;
				ar = ApplicationResource.builder().entity(
						ApplicationEntity.builder().name("testapp"+i).state("STARTED").build()
					).metadata(
							Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(APP_UUID_PREFIX+i).build()
					).build();
			
				list.add(ar);
				
			}
			ListApplicationsResponse resp = ListApplicationsResponse.builder().addAllResources(list).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		log.error("Invalid retrieveAllApplicationIdsInSpace request");
		return null;
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllOrgIds()
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		return this.retrieveOrgId("simorg");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceIdsInOrg(java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		return this.retrieveSpaceId(ORG_UUID, "simspace");
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		if (spaceId.equals(SPACE_UUID)) {
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
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomains(String orgId) {		
		List<DomainResource> domains = new ArrayList<DomainResource>();

		for (int i = 1;i<=100;i++) {
			
			DomainResource domain = DomainResource.builder()
			.entity(
				DomainEntity.builder()
				.name(SHARED_DOMAIN)
				.internal(false)
				.build())
			.metadata(
				Metadata.builder().id(SHARED_DOMAIN_UUID+i).createdAt(CREATED_AT_TIMESTAMP).build())    
			.build();

			domains.add(domain);				
		}

		DomainResource domain = DomainResource.builder()
		.entity(
			DomainEntity.builder()
			.name(INTERNAL_DOMAIN)
			.internal(true)
			.build())
		.metadata(
			Metadata.builder().id(INTERNAL_DOMAIN_UUID).createdAt(CREATED_AT_TIMESTAMP).build())    
		.build();

		domains.add(domain);	
		
		ListOrganizationDomainsResponse response = ListOrganizationDomainsResponse.builder().addAllResources(domains).build();
		return Mono.just(response);
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.applications.ListApplicationsResponse> retrieveAllApplicationIdsInSpaceV3(String orgId, String spaceId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId) {
		throw new UnsupportedOperationException();
	}
}
