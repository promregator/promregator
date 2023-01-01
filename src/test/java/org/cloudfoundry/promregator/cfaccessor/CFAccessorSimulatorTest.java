package org.cloudfoundry.promregator.cfaccessor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ApplicationState;
import org.cloudfoundry.client.v3.applications.ListApplicationProcessesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.OrganizationResource;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class CFAccessorSimulatorTest {


	@Test
	void testRetrieveOrgIdV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> mono = subject.retrieveOrgIdV3("simorg");
		org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse result = mono.block();
		
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getResources());
		Assertions.assertEquals(1, result.getResources().size());
		
		OrganizationResource or = result.getResources().get(0);
		Assertions.assertEquals(CFAccessorSimulator.ORG_UUID, or.getId());
	}
	
	@Test
	void testRetrieveAllOrgIdsV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> mono = subject.retrieveAllOrgIdsV3();
		org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse result = mono.block();
		
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getResources());
		Assertions.assertEquals(1, result.getResources().size());
		
		OrganizationResource or = result.getResources().get(0);
		Assertions.assertEquals(CFAccessorSimulator.ORG_UUID, or.getId());
	}

	@Test
	void testRetrieveSpaceIdV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> mono = subject.retrieveSpaceIdV3(CFAccessorSimulator.ORG_UUID, "simspace");
		org.cloudfoundry.client.v3.spaces.ListSpacesResponse result = mono.block();
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getResources());
		Assertions.assertEquals(1, result.getResources().size());
		Assertions.assertEquals(CFAccessorSimulator.SPACE_UUID, result.getResources().get(0).getId());
	}
	

	@Test
	void testRetrieveAllDomainsV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListOrganizationDomainsResponse> mono = subject.retrieveAllDomainsV3(CFAccessorSimulator.ORG_UUID);
		ListOrganizationDomainsResponse result = mono.block();
		
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getResources());
		Assertions.assertEquals(101, result.getResources().size());
		
		for(int i = 0;i<=99;i++) {
			int domainSequenceId = i + 1;
			DomainResource item = result.getResources().get(i);
			
			Assertions.assertEquals(CFAccessorSimulator.SHARED_DOMAIN, item.getName());
			Assertions.assertFalse(item.isInternal());
			Assertions.assertTrue(item.getId().contains(CFAccessorSimulator.SHARED_DOMAIN_UUID+domainSequenceId));
		}

		// get the shared domain
		DomainResource sharedDomain = result.getResources().get(100);
		Assertions.assertTrue(sharedDomain.isInternal());
		Assertions.assertEquals(CFAccessorSimulator.INTERNAL_DOMAIN, sharedDomain.getName());
	}
	
	@Test
	void testRetrieveAllSpacesV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> mono = subject.retrieveSpaceIdsInOrgV3(CFAccessorSimulator.ORG_UUID);
		org.cloudfoundry.client.v3.spaces.ListSpacesResponse result = mono.block();
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getResources());
		Assertions.assertEquals(1, result.getResources().size());
		Assertions.assertEquals(CFAccessorSimulator.SPACE_UUID, result.getResources().get(0).getId());
	}
	
	@Test
	void testRetrieveAllApplicationsInSpaceV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		Mono<ListApplicationsResponse> retrieveAllApplicationsInSpaceV3 = subject.retrieveAllApplicationsInSpaceV3(CFAccessorSimulator.ORG_UUID, CFAccessorSimulator.SPACE_UUID);
		ListApplicationsResponse result = retrieveAllApplicationsInSpaceV3.block();
		
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getResources());
		List<ApplicationResource> resources = result.getResources();
		Assertions.assertEquals(100, resources.size());
		
		for(int i = 0;i<=99;i++) {
			int appSequenceId = i + 1;
			ApplicationResource item = resources.get(i);
			
			Assertions.assertEquals(ApplicationState.STARTED, item.getState());
			Assertions.assertEquals(CFAccessorSimulator.APP_UUID_PREFIX+appSequenceId, item.getId());
		}

	}
	
	@Test
	void testRetrieveRoutesV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListRoutesResponse> routesForAppId = subject.retrieveRoutesForAppId(CFAccessorSimulator.APP_UUID_PREFIX+"50");
		
		ListRoutesResponse listRoutesResponse = routesForAppId.block();
		Assertions.assertNotNull(listRoutesResponse);
		
		List<RouteResource> resources = listRoutesResponse.getResources();
		Assertions.assertNotNull(resources);
		
		Assertions.assertEquals(1, resources.size());
		RouteResource routeResource = resources.get(0);
		
		Assertions.assertEquals(CFAccessorSimulator.ROUTE_UUID_PREFIX+"50", routeResource.getId());
		Assertions.assertEquals(CFAccessorSimulator.APP_HOST_PREFIX+"50."+CFAccessorSimulator.SHARED_DOMAIN, routeResource.getUrl());
		Assertions.assertEquals(CFAccessorSimulator.APP_ROUTE_PATH, routeResource.getPath());
	}

	@Test
	void testRetrieveRoutesV3Multiple() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListRoutesResponse> routesForAppId = subject.retrieveRoutesForAppIds(new HashSet<>(Arrays.asList(CFAccessorSimulator.APP_UUID_PREFIX+"50", CFAccessorSimulator.APP_UUID_PREFIX+"51")));
		
		ListRoutesResponse listRoutesResponse = routesForAppId.block();
		Assertions.assertNotNull(listRoutesResponse);
		
		List<RouteResource> resources = listRoutesResponse.getResources();
		Assertions.assertNotNull(resources);
		
		Assertions.assertEquals(2, resources.size());
		
		// Order within resources is not defined
		resources = resources.stream().sorted(new Comparator<RouteResource>() {
			@Override
			public int compare(RouteResource o1, RouteResource o2) {
				return o1.getId().compareTo(o2.getId());
			}
		}).toList();
		
		RouteResource routeResource = resources.get(0);
		Assertions.assertEquals(CFAccessorSimulator.ROUTE_UUID_PREFIX+"50", routeResource.getId());
		Assertions.assertEquals(CFAccessorSimulator.APP_HOST_PREFIX+"50."+CFAccessorSimulator.SHARED_DOMAIN, routeResource.getUrl());
		Assertions.assertEquals(CFAccessorSimulator.APP_ROUTE_PATH, routeResource.getPath());
		
		
		routeResource = resources.get(1);
		Assertions.assertEquals(CFAccessorSimulator.ROUTE_UUID_PREFIX+"51", routeResource.getId());
		Assertions.assertEquals(CFAccessorSimulator.APP_HOST_PREFIX+"51."+CFAccessorSimulator.SHARED_DOMAIN, routeResource.getUrl());
		Assertions.assertEquals(CFAccessorSimulator.APP_ROUTE_PATH, routeResource.getPath());
	}
	
	@Test
	void testRetrieveWebProcessesForApp() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListApplicationProcessesResponse> applicationProcessesResponse = subject.retrieveWebProcessesForApp(CFAccessorSimulator.APP_UUID_PREFIX+"50");
		
		ListApplicationProcessesResponse result = applicationProcessesResponse.block();
		Assertions.assertNotNull(result);
		
		List<ProcessResource> resources = result.getResources();
		Assertions.assertNotNull(resources);
		
		Assertions.assertEquals(1, resources.size());
		ProcessResource processResource = resources.get(0);
		
		Assertions.assertEquals(CFAccessorSimulator.PROCESS_UUID_PREFIX+"50", processResource.getId());
		Assertions.assertEquals("web", processResource.getType());
	}

}
