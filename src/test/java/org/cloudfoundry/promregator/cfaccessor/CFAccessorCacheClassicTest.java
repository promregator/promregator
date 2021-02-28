package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
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
@SpringBootTest(classes = CFAccessorCacheClassicSpringApplication.class)
@TestPropertySource(locations="../default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
class CFAccessorCacheClassicTest {

	@Autowired
	private CFAccessor parentMock;
	
	@Autowired
	private CFAccessorCacheClassic subject;
	
	@BeforeEach
	void invalidateCaches() {
		this.subject.invalidateCacheApplications();
		this.subject.invalidateCacheSpace();
		this.subject.invalidateCacheOrg();
		this.subject.invalidateCacheDomain();
	}
	
	@AfterAll
	static void runCleanup() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testRetrieveOrgId() {
		Mono<ListOrganizationsResponse> response1 = subject.retrieveOrgId("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
		
		Mono<ListOrganizationsResponse> response2 = subject.retrieveOrgId("dummy");
		assertThat(response1).isEqualTo(response2);
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
	}

	@Test
	void testRetrieveSpaceId() {
		
		Mono<ListSpacesResponse> response1 = subject.retrieveSpaceId("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceId("dummy1", "dummy2");
		
		Mono<ListSpacesResponse> response2 = subject.retrieveSpaceId("dummy1", "dummy2");
		assertThat(response1).isEqualTo(response2);
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
	void testRetrieveSpaceSummary() {
		Mono<GetSpaceSummaryResponse> response1 = subject.retrieveSpaceSummary("dummy");
		Mono<GetSpaceSummaryResponse> response2 = subject.retrieveSpaceSummary("dummy");
		assertThat(response1).isEqualTo(response2);
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceSummary("dummy");
	}

	
	@Test
	void testRetrieveDomains() {
		Mono<ListOrganizationDomainsResponse> response1 = subject.retrieveAllDomains("dummy");
		Mono<ListOrganizationDomainsResponse> response2 = subject.retrieveAllDomains("dummy");
		assertThat(response1).isEqualTo(response2);
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomains("dummy");
	}

}
