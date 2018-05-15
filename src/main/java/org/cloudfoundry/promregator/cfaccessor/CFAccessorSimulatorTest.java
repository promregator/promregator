package org.cloudfoundry.promregator.cfaccessor;

import static org.junit.Assert.fail;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsResponse;
import org.cloudfoundry.client.v2.routes.GetRouteResponse;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.junit.Assert;
import org.junit.Test;

import reactor.core.publisher.Mono;

public class CFAccessorSimulatorTest {

	@Test
	public void testRetrieveOrgId() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListOrganizationsResponse> mono = subject.retrieveOrgId("simorg");
		ListOrganizationsResponse result = mono.block();
		Assert.assertEquals(CFAccessorSimulator.ORG_UUID, result.getResources().get(0).getMetadata().getId());
	}

	@Test
	public void testRetrieveSpaceId() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListSpacesResponse> mono = subject.retrieveSpaceId(CFAccessorSimulator.ORG_UUID, "simspace");
		ListSpacesResponse result = mono.block();
		Assert.assertEquals(CFAccessorSimulator.SPACE_UUID, result.getResources().get(0).getMetadata().getId());
	}

	@Test
	public void testRetrieveApplicationId() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		for (int i = 0;i<10;i++) {
			Mono<ListApplicationsResponse> mono = subject.retrieveApplicationId(CFAccessorSimulator.ORG_UUID, CFAccessorSimulator.SPACE_UUID, "testapp"+i);
			ListApplicationsResponse result = mono.block();
			Assert.assertEquals(CFAccessorSimulator.APP_UUID_PREFIX+i, result.getResources().get(0).getMetadata().getId());
		}
	}

	@Test
	public void testRetrieveRouteMapping() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		Mono<ListRouteMappingsResponse> mono = subject.retrieveRouteMapping(CFAccessorSimulator.APP_UUID_PREFIX+"1");
		ListRouteMappingsResponse result = mono.block();
		Assert.assertEquals(CFAccessorSimulator.APP_ROUTE_UUID_PREFIX+"1", result.getResources().get(0).getEntity().getRouteId());
	}

	@Test
	public void testRetrieveRoute() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		Mono<GetRouteResponse> mono = subject.retrieveRoute(CFAccessorSimulator.APP_ROUTE_UUID_PREFIX+"1");
		GetRouteResponse result = mono.block();
		Assert.assertEquals(CFAccessorSimulator.APP_HOST_PREFIX+"1", result.getEntity().getHost());
		Assert.assertEquals(CFAccessorSimulator.SHARED_DOMAIN_UUID, result.getEntity().getDomainId());
	}

	@Test
	public void testRetrieveSharedDomain() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		Mono<GetSharedDomainResponse> mono = subject.retrieveSharedDomain(CFAccessorSimulator.SHARED_DOMAIN_UUID);
		GetSharedDomainResponse result = mono.block();
		Assert.assertEquals(CFAccessorSimulator.SHARED_DOMAIN, result.getEntity().getName());
	}

	@Test
	public void testRetrieveProcesses() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		for (int i = 0;i<10; i++) {
			String appId = CFAccessorSimulator.APP_UUID_PREFIX+i;
			Mono<ListProcessesResponse> mono = subject.retrieveProcesses(CFAccessorSimulator.ORG_UUID, CFAccessorSimulator.SPACE_UUID, appId);
			ListProcessesResponse result = mono.block();
			Assert.assertEquals(new Integer(2), result.getResources().get(0).getInstances());
		}
	}

}
