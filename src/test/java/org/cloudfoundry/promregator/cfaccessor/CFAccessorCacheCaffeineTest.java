package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = CFAccessorCacheCaffeineSpringApplication.class)
@TestPropertySource(locations="../default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
class CFAccessorCacheCaffeineTest {

	@Autowired
	private CFAccessor parentMock;
	
	@Autowired
	private CFAccessorCacheCaffeine subject;
	
	@BeforeEach
	public void invalidateCaches() {
		this.subject.invalidateCacheApplications();
		this.subject.invalidateCacheSpace();
		this.subject.invalidateCacheOrg();
		this.subject.invalidateCacheDomain();
	}
	
	@AfterAll
	public static void runCleanup() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testRetrieveOrgId() throws InterruptedException {
		Mono<ListOrganizationsResponse> response1 = subject.retrieveOrgId("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
		
		Thread.sleep(10);
		
		Mono<ListOrganizationsResponse> response2 = subject.retrieveOrgId("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
	}

	@Test
	void testRetrieveSpaceId() {
		
		Mono<ListSpacesResponse> response1 = subject.retrieveSpaceId("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceId("dummy1", "dummy2");
		
		Mono<ListSpacesResponse> response2 = subject.retrieveSpaceId("dummy1", "dummy2");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
	}

	@Test
	void testRetrieveAllApplicationIdsInSpace() {
		subject.retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		
		subject.retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
	}

	@Test
	void testRetrieveAllOrgIds() {
		subject.retrieveAllOrgIds();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllOrgIds();
	}
	
	@Test
	void testRetrieveSpaceSummary() {
		Mono<GetSpaceSummaryResponse> response1 = subject.retrieveSpaceSummary("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceSummary("dummy");
		
		Mono<GetSpaceSummaryResponse> response2 = subject.retrieveSpaceSummary("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceSummary("dummy");
	}

	@Test
	void testRetrieveDomain() {
		Mono<ListOrganizationDomainsResponse> response1 = subject.retrieveAllDomains("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomains("dummy");
		
		Mono<ListOrganizationDomainsResponse> response2 = subject.retrieveAllDomains("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomains("dummy");
	}

	@Test
	void testRetrieveOrgIdV3() throws InterruptedException {
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response1 = subject.retrieveOrgIdV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgIdV3("dummy");

		Thread.sleep(10);

		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> response2 = subject.retrieveOrgIdV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveOrgIdV3("dummy");
	}

	@Test
	void testRetrieveSpaceIdV3() {

		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response1 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdV3("dummy1", "dummy2");

		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> response2 = subject.retrieveSpaceIdV3("dummy1", "dummy2");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveSpaceIdV3("dummy1", "dummy2");
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
	void testRetrieveSpaceSummaryV3() {
		Mono<GetSpaceResponse> response1 = subject.retrieveSpaceV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceV3("dummy");

		Mono<GetSpaceResponse> response2 = subject.retrieveSpaceV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveSpaceV3("dummy");
	}

	@Test
	void testRetrieveDomainV3() {
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> response1 = subject.retrieveAllDomainsV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomainsV3("dummy");

		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> response2 = subject.retrieveAllDomainsV3("dummy");
		assertThat(response1.block()).isEqualTo(response2.block());
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllDomainsV3("dummy");
	}
}
