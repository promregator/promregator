package org.cloudfoundry.promregator.cfaccessor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;

import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import reactor.core.publisher.Mono;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = CFAccessorCacheCaffeineSpringApplication.class)
@TestPropertySource(locations="../default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class CFAccessorCacheCaffeineTest {

	@Autowired
	private CFAccessor parentMock;
	
	@Autowired
	private CFAccessorCacheCaffeine subject;
	
	@BeforeEach
	public void invalidateCaches() {
		this.subject.invalidateCacheApplication();
		this.subject.invalidateCacheSpace();
		this.subject.invalidateCacheOrg();
		this.subject.invalidateCacheDomain();
	}
	
	@BeforeEach
	public void clearMockCounters() {
		Mockito.reset(this.parentMock);
	}
	
	@AfterAll
	public static void runCleanup() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testRetrieveOrgId() throws InterruptedException {
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response1 = subject.retrieveOrgIdV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgIdV3("dummy");
		
		Thread.sleep(10);
		
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response2 = subject.retrieveOrgIdV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgIdV3("dummy");
	}

	@Test
	void testRetrieveSpaceId() {
		
		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response1 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdV3("dummy1", "dummy2");
		
		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response2 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(0)).retrieveOrgIdV3("dummy");
	}

	@Test
	void testRetrieveAllApplicationIdsInSpace() {
		subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		
		subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
	}

	@Test
	void testRetrieveAllOrgIds() {
		subject.retrieveAllOrgIdsV3();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllOrgIdsV3();
	}

	@Test
	void testRetrieveDomain() {
		Mono<ListOrganizationDomainsResponse> response1 = subject.retrieveAllDomainsV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomainsV3("dummy");
		
		Mono<ListOrganizationDomainsResponse> response2 = subject.retrieveAllDomainsV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomainsV3("dummy");
	}

	@Test
	void testRetrieveOrgIdV3() throws InterruptedException {
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response1 = subject.retrieveOrgIdV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgIdV3("dummy");

		Thread.sleep(10);

		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response2 = subject.retrieveOrgIdV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgIdV3("dummy");
	}

	@Test
	void testRetrieveSpaceIdV3() {

		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response1 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdV3("dummy1", "dummy2");

		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response2 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdV3("dummy1", "dummy2");
	}

	@Test
	void testRetrieveAllApplicationIdsInSpaceV3() {
		subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");

		subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
	}

	@Test
	void testRetrieveAllOrgIdsV3() {
		subject.retrieveAllOrgIdsV3();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllOrgIdsV3();
	}

	@Test
	void testRetrieveDomainV3() {
		Mono<ListOrganizationDomainsResponse> response1 = subject.retrieveAllDomainsV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomainsV3("dummy");

		Mono<ListOrganizationDomainsResponse> response2 = subject.retrieveAllDomainsV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomainsV3("dummy");
	}
	
	@Test
	void testRetrieveSpaceIdsInOrgV3() {
		Mono<ListSpacesResponse> response1 = subject.retrieveSpaceIdsInOrgV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdsInOrgV3("dummy");

		Mono<ListSpacesResponse> response2 = subject.retrieveSpaceIdsInOrgV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdsInOrgV3("dummy");
	}
	
	@Test
	void testRetrieveProcessesForApp() {
		Mono<ListProcessesResponse> response1 = subject.retrieveWebProcessesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveWebProcessesForAppId("dummy");

		Mono<ListProcessesResponse> response2 = subject.retrieveWebProcessesForAppId("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveWebProcessesForAppId("dummy");
	}
	
	@Test
	void testRetrieveRoutesForAppId() {
		Mono<ListRoutesResponse> response1 = subject.retrieveRoutesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.times(0)).retrieveRoutesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveRoutesForAppIds(Mockito.anySet());

		Mono<ListRoutesResponse> response2 = subject.retrieveRoutesForAppId("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(0)).retrieveRoutesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveRoutesForAppIds(Mockito.anySet());
	}
	
	@Test
	void testRetrieveRoutesForAppIds() {
		HashSet<String> set = new HashSet<>(Arrays.asList("dummy"));
		Mono<ListRoutesResponse> response1 = subject.retrieveRoutesForAppIds(set);
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveRoutesForAppIds(set);

		Mono<ListRoutesResponse> response2 = subject.retrieveRoutesForAppIds(set);
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveRoutesForAppIds(set); /* not cached */
	}
}
