package org.cloudfoundry.promregator.cfaccessor;

import java.util.List;

import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.OrganizationResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class CFAccessorSimulatorTest {

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
	void testRetrieveRoutes3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.retrieveRoutesForAppId("simapp"));
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

}
