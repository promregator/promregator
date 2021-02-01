package org.cloudfoundry.promregator.cfaccessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.domains.DomainRelationships;
import org.cloudfoundry.client.v3.domains.GetDomainResponse;
import org.cloudfoundry.client.v3.routes.Application;
import org.cloudfoundry.client.v3.routes.Destination;
import org.cloudfoundry.client.v3.routes.Process;
import org.cloudfoundry.client.v3.routes.RouteRelationships;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.junit.jupiter.api.Assertions;

import reactor.core.publisher.Mono;

public class CFAccessorMock implements CFAccessor {
	public static final String UNITTEST_ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID_DOESNOTEXIST = "db08be9a-2fa4-11e8-b467-0ed5f89f718b-doesnotexist";
	public static final String UNITTEST_SPACE_UUID_EXCEPTION = "db08be9a-2fa4-11e8-b467-0ed5f89f718b-exception";
	public static final String UNITTEST_APP1_UUID = "55820b2c-2fa5-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP2_UUID = "5a0ead6c-2fa5-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP1_ROUTE_UUID = "57ac2ada-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP2_ROUTE_UUID = "5c5b464c-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP1_HOST = "hostapp1";
	public static final String UNITTEST_APP2_HOST = "hostapp2";
	public static final String UNITTEST_SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
  public static final String UNITTEST_SHARED_DOMAIN = "shared.domain.example.org";

  public static final String UNITTEST_INTERNAL_DOMAIN_UUID = "49225c7e-b4c3-45b2-b796-7bb9c64dc79d";
  public static final String UNITTEST_INTERNAL_DOMAIN = "apps.internal";
  
  public static final String UNITTEST_APP_INTERNAL_UUID = "a8762694-95ce-4c3c-a4fb-250e28187a0a";
  public static final String UNITTEST_APP_INTERNAL_HOST = "internal-app";
	
	public static final String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public static final String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		
		if ("unittestorg".equalsIgnoreCase(orgName)) {
			
			OrganizationResource or = OrganizationResource.builder().entity(
					OrganizationEntity.builder().name("unittestorg").build()
				).metadata(
					Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_ORG_UUID).build()
					// Note that UpdatedAt is not set here, as this can also happen in real life!
				).build();
			
			List<org.cloudfoundry.client.v2.organizations.OrganizationResource> list = new LinkedList<>();
			list.add(or);
			
			ListOrganizationsResponse resp = org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse.builder().addAllResources(list).build();
			
			return Mono.just(resp);
		} else if ("doesnotexist".equals(orgName)) {
			return Mono.just(org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse.builder().resources(new ArrayList<>()).build());
		} else if ("exception".equals(orgName)) {
			return Mono.just(org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse.builder().build())
					.map(x -> {throw new Error("exception org name provided");});
		}
		Assertions.fail("Invalid OrgId request");
		return null;
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		if (orgId.equals(UNITTEST_ORG_UUID)) {
			if ( "unittestspace".equalsIgnoreCase(spaceName)) {
				SpaceResource sr = SpaceResource.builder().entity(
						SpaceEntity.builder().name("unittestspace").build()
					).metadata(
						Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_SPACE_UUID).build()
					).build();
				List<SpaceResource> list = new LinkedList<>();
				list.add(sr);
				ListSpacesResponse resp = ListSpacesResponse.builder().addAllResources(list).build();
				return Mono.just(resp);
			} else if ( "unittestspace-summarydoesnotexist".equals(spaceName)) {
				SpaceResource sr = SpaceResource.builder().entity(
						SpaceEntity.builder().name(spaceName).build()
					).metadata(
						Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_SPACE_UUID_DOESNOTEXIST).build()
					).build();
				List<SpaceResource> list = new LinkedList<>();
				list.add(sr);
				ListSpacesResponse resp = ListSpacesResponse.builder().addAllResources(list).build();
				return Mono.just(resp);
			} else if ( "unittestspace-summaryexception".equals(spaceName)) {
				SpaceResource sr = SpaceResource.builder().entity(
						SpaceEntity.builder().name(spaceName).build()
					).metadata(
						Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_SPACE_UUID_EXCEPTION).build()
					).build();
				List<SpaceResource> list = new LinkedList<>();
				list.add(sr);
				ListSpacesResponse resp = ListSpacesResponse.builder().addAllResources(list).build();
				return Mono.just(resp);
			} else if ("doesnotexist".equals(spaceName)) {
				return Mono.just(ListSpacesResponse.builder().resources(new ArrayList<>()).build());
			} else if ("exception".equals(spaceName)) {
				return Mono.just(ListSpacesResponse.builder().build()).map(x -> { throw new Error("exception space name provided"); });
			}
		}
		
		Assertions.fail("Invalid SpaceId request");
		return null;
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		if (orgId.equals(UNITTEST_ORG_UUID) && spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<ApplicationResource> list = new LinkedList<>();

			ApplicationResource ar = ApplicationResource.builder().entity(
					ApplicationEntity.builder().name("testapp").state("STARTED").build()
				).metadata(
						Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP1_UUID).build()
				).build();
			list.add(ar);
			
			ar = ApplicationResource.builder().entity(
					ApplicationEntity.builder().name("testapp2").state("STARTED").build()
				).metadata(
						Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP2_UUID).build()
				).build();
      list.add(ar);
      
      ar = ApplicationResource.builder().entity(
					ApplicationEntity.builder().name("internalapp").state("STARTED").build()
				).metadata(
						Metadata.builder().createdAt(CREATED_AT_TIMESTAMP).id(UNITTEST_APP_INTERNAL_UUID).build()
				).build();
			list.add(ar);			
			
			ListApplicationsResponse resp = ListApplicationsResponse.builder().addAllResources(list).build();
			return Mono.just(resp);
    } else if (UNITTEST_SPACE_UUID_DOESNOTEXIST.equals(spaceId)) {
			return Mono.just(ListApplicationsResponse.builder().build());
		} else if (UNITTEST_SPACE_UUID_EXCEPTION.equals(spaceId)) {
			return Mono.just(ListApplicationsResponse.builder().build()).map( x-> { throw new Error("exception on AllAppIdsInSpace"); });
		}
		
		Assertions.fail("Invalid process request");
		return null;
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		if (spaceId.equals(UNITTEST_SPACE_UUID)) {
			List<SpaceApplicationSummary> list = new LinkedList<>();
			
			final String[] urls1 = { UNITTEST_APP1_HOST + "." + UNITTEST_SHARED_DOMAIN }; 
			SpaceApplicationSummary sas = SpaceApplicationSummary.builder()
					.id(UNITTEST_APP1_UUID)
					.name("testapp")
					.addAllUrls(Arrays.asList(urls1))
					.instances(2)
					.build();
			list.add(sas);
			
			final String[] urls2 = { UNITTEST_APP2_HOST + "." + UNITTEST_SHARED_DOMAIN + "/additionalPath",
					UNITTEST_APP2_HOST + ".additionalSubdomain." + UNITTEST_SHARED_DOMAIN + "/additionalPath" }; 
			sas =  SpaceApplicationSummary.builder()
					.id(UNITTEST_APP2_UUID)
					.name("testapp2")
					.addAllUrls(Arrays.asList(urls2))
					.instances(1)
					.build();
      list.add(sas);
      
      final String[] urls3 = { UNITTEST_APP_INTERNAL_HOST + "." + UNITTEST_INTERNAL_DOMAIN }; 
			sas = SpaceApplicationSummary.builder()
					.id(UNITTEST_APP_INTERNAL_UUID)
					.name("internalapp")
					.addAllUrls(Arrays.asList(urls3))
					.instances(2)
					.build();
			list.add(sas);
			
			GetSpaceSummaryResponse resp = GetSpaceSummaryResponse.builder().addAllApplications(list).build();
			
			return Mono.just(resp);
		} else if (spaceId.equals(UNITTEST_SPACE_UUID_DOESNOTEXIST)) {
			return Mono.just(GetSpaceSummaryResponse.builder().build());
		} else if (spaceId.equals(UNITTEST_SPACE_UUID_EXCEPTION)) {
			return Mono.just(GetSpaceSummaryResponse.builder().build()).map( x-> { throw new Error("exception on application summary"); });
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
				.description("CFAccessorMock")
				.name("CFAccessorMock")
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
		GetDomainResponse response;
				
		if(domainId == null)
		{									
			return Mono.empty();
		}

    if(domainId.equals(UNITTEST_INTERNAL_DOMAIN_UUID)) {  
      response = GetDomainResponse.builder()
      .name(UNITTEST_INTERNAL_DOMAIN)
      .id(UNITTEST_INTERNAL_DOMAIN_UUID)
      .createdAt(CREATED_AT_TIMESTAMP)
      .isInternal(true)
      .relationships(
        DomainRelationships.builder()
        .organization(
          ToOneRelationship.builder()
          .data(
            Relationship.builder().id(UNITTEST_ORG_UUID).build()
          ).build()
        ).build()
      )
      .build();      
    } else {
      response = GetDomainResponse.builder()
      .name(UNITTEST_SHARED_DOMAIN)
      .id(UNITTEST_SHARED_DOMAIN_UUID)
      .createdAt(CREATED_AT_TIMESTAMP)
      .isInternal(false)
      .relationships(
        DomainRelationships.builder()
        .organization(
          ToOneRelationship.builder()
          .data(
            Relationship.builder().id(UNITTEST_ORG_UUID).build()
          ).build()
        ).build()
      )
      .build();      
    }
		return Mono.just(response);
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveAppRoutes(String appId) {
		List<RouteResource> routes = new LinkedList<>();
		
		if (appId == null) {			
			return Mono.empty();
		}
    
    if(appId.equals(UNITTEST_APP_INTERNAL_UUID)) {
      RouteResource res = RouteResource.builder()
      .id("id")
      .createdAt(CREATED_AT_TIMESTAMP)
      .host(UNITTEST_APP_INTERNAL_HOST)    
      .path("path")
      .url(UNITTEST_APP_INTERNAL_HOST + "." + UNITTEST_INTERNAL_DOMAIN)
      .relationships(
        RouteRelationships.builder()
        .domain(
          ToOneRelationship.builder().data(
            Relationship.builder().id(UNITTEST_INTERNAL_DOMAIN_UUID).build()
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
          .applicationId(UNITTEST_APP_INTERNAL_UUID)
          .process(
            Process.builder().type("web").build()
          ).build())
        .build()
      ).build();
    
      routes.add(res);
		} else {
      RouteResource res = RouteResource.builder()
      .id("id")
      .createdAt(CREATED_AT_TIMESTAMP)
      .host(UNITTEST_APP1_HOST)    
      .path("path")
      .url(UNITTEST_APP1_HOST + "." + UNITTEST_SHARED_DOMAIN)
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
          .applicationId(UNITTEST_APP1_UUID)
          .process(
            Process.builder().type("web").build()
          ).build())
        .build()
      ).build();
    
      routes.add(res);

      res = RouteResource.builder()
      .id("id")
      .createdAt(CREATED_AT_TIMESTAMP)
      .host(UNITTEST_APP2_HOST)    
      .path("path")
      .url(UNITTEST_APP2_HOST + "." + UNITTEST_SHARED_DOMAIN)
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
          .applicationId(UNITTEST_APP2_UUID)
          .process(
            Process.builder().type("web").build()
          ).build())
        .build()
      ).build();
    
			routes.add(res);
			
			res = RouteResource.builder()
      .id("id")
      .createdAt(CREATED_AT_TIMESTAMP)
      .host(UNITTEST_APP2_HOST)    
      .path("path")
      .url(UNITTEST_APP2_HOST + ".additionalSubdomain." + UNITTEST_SHARED_DOMAIN)
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
          .applicationId(UNITTEST_APP2_UUID)
          .process(
            Process.builder().type("web").build()
          ).build())
        .build()
      ).build();
    
      routes.add(res);
    }

    ListApplicationRoutesResponse resp = ListApplicationRoutesResponse.builder().addAllResources(routes).build();
		return Mono.just(resp);
	}
}
