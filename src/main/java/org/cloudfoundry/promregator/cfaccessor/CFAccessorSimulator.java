package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.applications.ApplicationEntity;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationEntity;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.servicebindings.ServiceBindingEntity;
import org.cloudfoundry.client.v2.servicebindings.ServiceBindingResource;
import org.cloudfoundry.client.v2.spaces.GetSpaceResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v2.spaces.SpaceEntity;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstancesResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.UserProvidedServiceInstanceEntity;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.UserProvidedServiceInstanceResource;

import reactor.core.publisher.Mono;

public class CFAccessorSimulator implements CFAccessor {
	public final static String ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public final static String SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public final static String APP_UUID_PREFIX = "55820b2c-2fa5-11e8-b467-";
	public final static String APP_HOST_PREFIX = "hostapp";
	public final static String SHARED_DOMAIN = "shared.domain.example.org";
	
	public final static String UPS_APP_UUID_PREFIX = "05820b2c-2fa5-11e8-b467-";
	
	public final static String UPS_GLOBAL_UPS_UUID = "ce364c9d-fea0-4ea9-89d6-530bc1fa939c";
	public final static String UPS_APPOWN_UPS_UUID_PREFIX = "b13a936b-a41c-48cd-bcc0-";
	public final static String UPS_APP_HOST_PREFIX = "upshostapp";
	
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
				final String[] urls = { APP_HOST_PREFIX+i+"."+SHARED_DOMAIN }; 
				SpaceApplicationSummary sas = SpaceApplicationSummary.builder()
						.id(APP_UUID_PREFIX+i)
						.name("testapp"+i)
						.addAllUrls(Arrays.asList(urls))
						.instances(this.amountInstances)
						.state("STARTED")
						.build();
				list.add(sas);
			}
			
			for (int i = 1;i<=100;i++) {
				final String[] urls = { UPS_APP_HOST_PREFIX+i+"."+SHARED_DOMAIN }; 
				SpaceApplicationSummary sas = SpaceApplicationSummary.builder()
						.id(UPS_APP_UUID_PREFIX+i)
						.name("upstestapp"+i)
						.addAllUrls(Arrays.asList(urls))
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
	public Mono<ListUserProvidedServiceInstancesResponse> retrieveAllUserProvidedServicesPromregatorRelevant() {
		List<UserProvidedServiceInstanceResource> res = new LinkedList<>();
		
		/* adding global UPS */
		Metadata metadata = Metadata.builder().id(UPS_GLOBAL_UPS_UUID).createdAt(CREATED_AT_TIMESTAMP).build();
		UserProvidedServiceInstanceEntity global_entity = UserProvidedServiceInstanceEntity.builder()
				.credential("promregator-version", 1)
				.credential("path", "/global_metrics")
				.credential("username", "user")
				.credential("password", "password")
				.name("global_prometheus_ups")
				.spaceId(SPACE_UUID)
				.build();
		res.add(UserProvidedServiceInstanceResource.builder().metadata(metadata).entity(global_entity).build());
		
		for (int i = 1;i<=100;i++) {
			metadata = Metadata.builder().id(UPS_APPOWN_UPS_UUID_PREFIX+i).createdAt(CREATED_AT_TIMESTAMP).build();
			UserProvidedServiceInstanceEntity appown_entity = UserProvidedServiceInstanceEntity.builder()
					.credential("promregator-version", 1)
					.credential("path", "/appown_metrics")
					.credential("username", "user")
					.credential("password", "password")
					.name("prometheus_ups_for_app_"+i)
					.spaceId(SPACE_UUID)
					.build();
			res.add(UserProvidedServiceInstanceResource.builder().metadata(metadata).entity(appown_entity).build());
		}
		
		ListUserProvidedServiceInstancesResponse resp = ListUserProvidedServiceInstancesResponse.builder().addAllResources(res).build();
		return Mono.just(resp).delayElement(this.getSleepRandomDuration());
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpace(String spaceId) {
		if (spaceId.equals(SPACE_UUID)) {
			Metadata metadata = Metadata.builder().id(spaceId).createdAt(CREATED_AT_TIMESTAMP).build();
			SpaceEntity entity = SpaceEntity.builder().name("simspace").organizationId(ORG_UUID).build();
			GetSpaceResponse resp = GetSpaceResponse.builder().metadata(metadata).entity(entity).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid retrieveSpace request");
		return null;
	}

	@Override
	public Mono<GetOrganizationResponse> retrieveOrg(String orgId) {
		if (orgId.equals(orgId)) {
			Metadata metadata = Metadata.builder().id(orgId).createdAt(CREATED_AT_TIMESTAMP).build();
			
			OrganizationEntity entity = OrganizationEntity.builder().name("simorg").build();
			GetOrganizationResponse resp = GetOrganizationResponse.builder().metadata(metadata).entity(entity ).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid retrieveOrg request");
		return null;
	}

	@Override
	public Mono<ListUserProvidedServiceInstanceServiceBindingsResponse> retrieveUserProvidedServiceBindings(
			String upsId) {
		if (upsId.equals(UPS_GLOBAL_UPS_UUID)) {
			List<ServiceBindingResource> bindings = new LinkedList<>();
			for (int i = 1;i<=100;i++) {
				ServiceBindingEntity entity = ServiceBindingEntity.builder().serviceInstanceId(upsId).applicationId(UPS_APP_UUID_PREFIX+i)
						.credential("promregator-version", 1)
						.credential("path", "/global_metrics")
						.credential("username", "user")
						.credential("password", "password")
						.build();
				bindings.add(ServiceBindingResource.builder().entity(entity).build());
			}
			ListUserProvidedServiceInstanceServiceBindingsResponse resp = ListUserProvidedServiceInstanceServiceBindingsResponse.builder().addAllResources(bindings).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		if (upsId.startsWith(UPS_APPOWN_UPS_UUID_PREFIX)) {
			int appIndex = Integer.parseInt(upsId.substring(UPS_APPOWN_UPS_UUID_PREFIX.length()));
			List<ServiceBindingResource> bindings = new LinkedList<>();
			ServiceBindingEntity entity = ServiceBindingEntity.builder().serviceInstanceId(upsId).applicationId(UPS_APP_UUID_PREFIX+appIndex)
					.credential("promregator-version", 1)
					.credential("path", "/appown_metrics")
					.credential("username", "user")
					.credential("password", "password")
					.build();
			bindings.add(ServiceBindingResource.builder().entity(entity).build());
			ListUserProvidedServiceInstanceServiceBindingsResponse resp = ListUserProvidedServiceInstanceServiceBindingsResponse.builder().addAllResources(bindings).build();
			return Mono.just(resp).delayElement(this.getSleepRandomDuration());
		}
		
		log.error("Invalid retrieveUserProvidedServiceBindings request");
		return null;
	}

}
