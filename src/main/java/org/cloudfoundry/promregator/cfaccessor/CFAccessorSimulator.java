package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
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
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceEntity;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.processes.Data;
import org.cloudfoundry.client.v3.processes.HealthCheck;
import org.cloudfoundry.client.v3.processes.HealthCheckType;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.processes.ProcessResource.Builder;

import reactor.core.publisher.Mono;

public class CFAccessorSimulator implements CFAccessor {
	public final static String ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public final static String SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public final static String APP_UUID_PREFIX = "55820b2c-2fa5-11e8-b467-";
	public final static String APP_ROUTE_UUID_PREFIX = "57ac2ada-2fa6-11e8-b467-";
	public final static String APP_HOST_PREFIX = "hostapp";
	public final static String SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
	public final static String SHARED_DOMAIN = "shared.domain.example.org";
	
	private static final Logger log = Logger.getLogger(CFAccessorSimulator.class);
	
	public final static String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public final static String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	
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
	public Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName) {
		if (orgId.equals(ORG_UUID) && spaceId.equals(SPACE_UUID)) {
			ApplicationResource ar = null;
			
			if (applicationName.startsWith("testapp")) {
				String appNumber = applicationName.substring(7);
				
				ar = ApplicationResource.builder().entity(
						ApplicationEntity.builder().name(applicationName).build()
					).metadata(
							Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(APP_UUID_PREFIX+appNumber).build()
					).build();
			} else {
				log.error("Invalid ApplicationId request, application name is invalid");
			}
			
			List<ApplicationResource> list = new LinkedList<>();
			list.add(ar);
			ListApplicationsResponse resp = ListApplicationsResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid ApplicationId request");
		return null;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		if (orgId.equals(ORG_UUID) && spaceId.equals(SPACE_UUID)) {
			List<ApplicationResource> list = new LinkedList<>();

			
			for (int i = 0;i<100;i++) {
				ApplicationResource ar = null;
				ar = ApplicationResource.builder().entity(
						ApplicationEntity.builder().name("testapp"+i).build()
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

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		// TODO Auto-generated method stub
		throw new Error("still to be implemented");
	}

}
