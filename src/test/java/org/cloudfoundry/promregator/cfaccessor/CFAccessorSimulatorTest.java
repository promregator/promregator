package org.cloudfoundry.promregator.cfaccessor;

import java.util.List;

import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class CFAccessorSimulatorTest {

	@Test
	void testRetrieveOrgId() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListOrganizationsResponse> mono = subject.retrieveOrgId("simorg");
		ListOrganizationsResponse result = mono.block();
		Assertions.assertEquals(CFAccessorSimulator.ORG_UUID, result.getResources().get(0).getMetadata().getId());
	}

	@Test
	void testRetrieveSpaceId() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListSpacesResponse> mono = subject.retrieveSpaceId(CFAccessorSimulator.ORG_UUID, "simspace");
		ListSpacesResponse result = mono.block();
		Assertions.assertEquals(CFAccessorSimulator.SPACE_UUID, result.getResources().get(0).getMetadata().getId());
	}

	@Test
	void testRetrieveSpaceSummary() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		
		Mono<GetSpaceSummaryResponse> mono = subject.retrieveSpaceSummary(CFAccessorSimulator.SPACE_UUID);
		GetSpaceSummaryResponse result = mono.block();
		
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getApplications());
		Assertions.assertEquals(100, result.getApplications().size());
		
		List<SpaceApplicationSummary> list = result.getApplications();
		
		boolean[] tests = new boolean[10+1];
		
		for(SpaceApplicationSummary item : list) {
			String appNumber = item.getId().substring(CFAccessorSimulator.APP_UUID_PREFIX.length());
			int appNumberInteger = Integer.parseInt(appNumber);
			
			Assertions.assertEquals(2, item.getInstances().intValue());
			Assertions.assertTrue(item.getUrls().contains(CFAccessorSimulator.APP_HOST_PREFIX+appNumber+"."+CFAccessorSimulator.SHARED_DOMAIN));
			
			if (appNumberInteger >= 11) {
				continue;
			}
			
			tests[appNumberInteger] = true;
		}
		
		for (int i = 1;i<=10;i++) {
			Assertions.assertTrue(tests[i]);
		}
	}

}
