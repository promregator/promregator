package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import reactor.core.publisher.Mono;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = CFAccessorCacheCaffeineSpringApplication.class)
@TestPropertySource(locations="../default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class CFAccessorCacheCaffeineTest {

	@Autowired
	private CFAccessor parentMock;
	
	@Autowired
	private CFAccessorCacheCaffeine subject;
	
	@Before
	public void invalidateCaches() {
		this.subject.invalidateCacheApplications();
		this.subject.invalidateCacheSpace();
		this.subject.invalidateCacheOrg();
	}
	
	@AfterClass
	public static void runCleanup() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	public void testRetrieveOrgId() throws InterruptedException {
		Mono<ListOrganizationsResponse> response1 = subject.retrieveOrgId("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
		
		Thread.sleep(10);
		
		Mono<ListOrganizationsResponse> response2 = subject.retrieveOrgId("dummy");
		Assert.assertEquals(response1.block(), response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
	}

	@Test
	public void testRetrieveSpaceId() {
		
		Mono<ListSpacesResponse> response1 = subject.retrieveSpaceId("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceId("dummy1", "dummy2");
		
		Mono<ListSpacesResponse> response2 = subject.retrieveSpaceId("dummy1", "dummy2");
		Assert.assertEquals(response1.block(), response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
	}

	@Test
	public void testRetrieveAllApplicationIdsInSpace() {
		subject.retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		
		subject.retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
	}

	@Test
	public void testRetrieveAllOrgIds() {
		subject.retrieveAllOrgIds();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllOrgIds();
	}
	
	@Test
	public void testRetrieveSpaceSummary() {
		Mono<GetSpaceSummaryResponse> response1 = subject.retrieveSpaceSummary("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceSummary("dummy");
		
		Mono<GetSpaceSummaryResponse> response2 = subject.retrieveSpaceSummary("dummy");
		Assert.assertEquals(response1.block(), response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceSummary("dummy");
	}

}
