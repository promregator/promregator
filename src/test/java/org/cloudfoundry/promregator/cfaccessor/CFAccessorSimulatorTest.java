package org.cloudfoundry.promregator.cfaccessor;

import java.util.List;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
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
	public void testRetrieveSpaceSummary() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		Mono<GetSpaceSummaryResponse> mono = subject.retrieveSpaceSummary(CFAccessorSimulator.SPACE_UUID);
		GetSpaceSummaryResponse result = mono.block();
		
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.getApplications());
		Assert.assertEquals(100, result.getApplications().size());
		
		List<SpaceApplicationSummary> list = result.getApplications();
		
		boolean[] tests = new boolean[10+1];
		
		for(SpaceApplicationSummary item : list) {
			String appNumber = item.getId().substring(CFAccessorSimulator.APP_UUID_PREFIX.length());
			int appNumberInteger = Integer.parseInt(appNumber);
			
			Assert.assertEquals(2, item.getInstances().intValue());
			Assert.assertTrue(item.getUrls().contains(CFAccessorSimulator.APP_HOST_PREFIX+appNumber+"."+CFAccessorSimulator.SHARED_DOMAIN));
			
			if (appNumberInteger >= 11) {
				continue;
			}
			
			tests[appNumberInteger] = true;
		}
		
		for (int i = 1;i<=10;i++) {
			Assert.assertTrue(tests[i]);
		}
	}

	

}
