package org.cloudfoundry.promregator.cfaccessor;


import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeoutException;

import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import reactor.core.publisher.Mono;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = CFAccessorCacheCaffeineTimeoutSpringApplication.class)
@TestPropertySource(locations= { "../default.properties" })
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class CFAccessorCacheCaffeineTimeoutTest {

	@Autowired
	private CFAccessor parentMock;
	
	@Autowired
	private CFAccessorCacheCaffeine subject;

	@BeforeEach
	void clearCaches() {
		this.subject.invalidateCacheApplication();
		this.subject.invalidateCacheSpace();
		this.subject.invalidateCacheOrg();
		this.subject.invalidateCacheDomain();
	}
	
	@BeforeEach
	void setupMocks() {
		Mockito.reset(this.parentMock);
		Mockito.when(this.parentMock.retrieveOrgIdV3("dummy")).then(new TimeoutMonoAnswer());
		Mockito.when(this.parentMock.retrieveSpaceIdV3("dummy1", "dummy2")).then(new TimeoutMonoAnswer());
		Mockito.when(this.parentMock.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2")).then(new TimeoutMonoAnswer());
		Mockito.when(this.parentMock.retrieveAllDomainsV3("dummy")).then(new TimeoutMonoAnswer());
		Mockito.when(this.parentMock.retrieveWebProcessesForAppId("dummy")).then(new TimeoutMonoAnswer());
		Mockito.when(this.parentMock.retrieveSpaceIdsInOrgV3("dummy")).then(new TimeoutMonoAnswer());
		Mockito.when(this.parentMock.retrieveAllOrgIdsV3()).then(new TimeoutMonoAnswer());
		Mockito.when(this.parentMock.retrieveRoutesForAppIds(Mockito.anySet())).then(new TimeoutMonoAnswer());
	}

	public static class TimeoutMonoAnswer implements Answer<Mono<?>> {
		@Override
		public Mono<?> answer(InvocationOnMock invocation) throws Throwable {
			return Mono.error(new TimeoutException("Unit test timeout raised"));
		}
	}


	@AfterAll
	static void runCleanup() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testRetrieveOrgId() throws InterruptedException {
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response1 = subject.retrieveOrgIdV3("dummy");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgIdV3("dummy");
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response2 = subject.retrieveOrgIdV3("dummy");
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveOrgIdV3("dummy");
	}

	@Test
	void testRetrieveSpaceId() throws InterruptedException {
		
		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response1 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdV3("dummy1", "dummy2");
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response2 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveSpaceIdV3("dummy1", "dummy2");
	}

	@Test
	void testRetrieveAllApplicationIdsInSpace() throws InterruptedException {
		Mono<ListApplicationsResponse> response1 = subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<ListApplicationsResponse> response2 = subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
	}

	@Test
	void testRetrieveDomains() throws InterruptedException {
		Mono<ListOrganizationDomainsResponse> response1 = subject.retrieveAllDomainsV3("dummy");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomainsV3("dummy");
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<ListOrganizationDomainsResponse> response2 = subject.retrieveAllDomainsV3("dummy");
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllDomainsV3("dummy");
	}

	@Test
	void testRetrieveAllOrgIdsV3() throws InterruptedException {
		Mono<ListOrganizationsResponse> response1 = subject.retrieveAllOrgIdsV3();
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllOrgIdsV3();
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<ListOrganizationsResponse> response2 = subject.retrieveAllOrgIdsV3();
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllOrgIdsV3();
	}
	
	@Test
	void testRetrieveSpaceIdsInOrgV3() throws InterruptedException {
		Mono<ListSpacesResponse> response1 = subject.retrieveSpaceIdsInOrgV3("dummy");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdsInOrgV3("dummy");
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<ListSpacesResponse> response2 = subject.retrieveSpaceIdsInOrgV3("dummy");
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveSpaceIdsInOrgV3("dummy");
	}
	
	@Test
	void testRetrieveProcessesForApp() throws InterruptedException {
		Mono<ListProcessesResponse> response1 = subject.retrieveWebProcessesForAppId("dummy");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveWebProcessesForAppId("dummy");
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<ListProcessesResponse> response2 = subject.retrieveWebProcessesForAppId("dummy");
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveWebProcessesForAppId("dummy");
	}
	
	@Test
	void testRetrieveRoutesForAppIds() throws InterruptedException {
		HashSet<String> set = new HashSet<>(Arrays.asList("dummy"));
		Mono<ListRoutesResponse> response1 = subject.retrieveRoutesForAppIds(set);
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveRoutesForAppIds(set);
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<ListRoutesResponse> response2 = subject.retrieveRoutesForAppIds(set);
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveRoutesForAppIds(set);
	}
	
	@Test
	void testRetrieveRoutesForAppId() throws InterruptedException {
		Mono<ListRoutesResponse> response1 = subject.retrieveRoutesForAppId("dummy");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveRoutesForAppIds(Mockito.anySet());
		
		// required to permit asynchronous updates of caches => test stability
		Thread.sleep(10);
		
		Mono<ListRoutesResponse> response2 = subject.retrieveRoutesForAppId("dummy");
		response2.subscribe();
		Assertions.assertNotEquals(response1, response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveRoutesForAppIds(Mockito.anySet());
	}
}
