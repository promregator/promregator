package org.cloudfoundry.promregator.cfaccessor;

import java.util.List;

import org.cloudfoundry.client.v2.domains.DomainResource;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
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

	@Test
	void testRetrieveAllDomains() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Mono<ListOrganizationDomainsResponse> mono = subject.retrieveAllDomains(CFAccessorSimulator.ORG_UUID);
		ListOrganizationDomainsResponse result = mono.block();
		
		Assertions.assertNotNull(result);
		Assertions.assertNotNull(result.getResources());
		Assertions.assertEquals(101, result.getResources().size());
		
		for(int i = 0;i<=99;i++) {		
			int domainSequenceId = i + 1;	
			DomainResource item = result.getResources().get(i);
			
			Assertions.assertEquals(CFAccessorSimulator.SHARED_DOMAIN, item.getEntity().getName());			
			Assertions.assertFalse(item.getEntity().getInternal());
			Assertions.assertTrue(item.getMetadata().getId().contains(CFAccessorSimulator.SHARED_DOMAIN_UUID+domainSequenceId));
		}

		// get the shared domain
		DomainResource sharedDomain = result.getResources().get(100);
		Assertions.assertTrue(sharedDomain.getEntity().getInternal());
		Assertions.assertEquals(CFAccessorSimulator.INTERNAL_DOMAIN, sharedDomain.getEntity().getName());
	}

	@Test
	void testRetrieveOrgIdV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.retrieveOrgId("simorg"));
	}

	@Test
	void testRetrieveSpaceIdV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.retrieveSpaceIdV3(CFAccessorSimulator.ORG_UUID, "simspace"));
	}

	@Test
	void testRetrieveAllDomainsV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.retrieveAllDomainsV3(CFAccessorSimulator.ORG_UUID));
	}

	@Test
	void testRetrieveRoutes3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.retrieveRoutesForAppId("simapp"));
	}

	@Test
	void testRetrieveAllSpacesV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.retrieveSpaceIdsInOrgV3(CFAccessorSimulator.ORG_UUID));
	}

	@Test
	void testRetrieveSpaceV3() {
		CFAccessorSimulator subject = new CFAccessorSimulator(2);
		Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.retrieveSpaceV3("simspace"));
	}
}
