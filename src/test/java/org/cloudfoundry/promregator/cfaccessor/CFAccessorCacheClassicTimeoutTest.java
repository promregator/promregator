package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
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
@SpringBootTest(classes = CFAccessorCacheClassicTimeoutSpringApplication.class)
@TestPropertySource(locations= { "../default.properties" })
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class CFAccessorCacheClassicTimeoutTest {

	@Autowired
	private CFAccessor parentMock;
	
	@Autowired
	private CFAccessorCacheClassic subject;
	
	@BeforeEach
	public void invalidateCaches() {
		this.subject.invalidateCacheApplications();
		this.subject.invalidateCacheSpace();
		this.subject.invalidateCacheOrg();
	}
	
	@AfterAll
	public static void runCleanup() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	public void testRetrieveOrgId() {
		Mono<ListOrganizationsResponse> response1 = subject.retrieveOrgId("dummy");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgId("dummy");
		
		Mono<ListOrganizationsResponse> response2 = subject.retrieveOrgId("dummy");
		response2.subscribe();
		assertThat(response1).isNotEqualTo(response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveOrgId("dummy");
	}

	@Test
	public void testRetrieveSpaceId() {
		
		Mono<ListSpacesResponse> response1 = subject.retrieveSpaceId("dummy1", "dummy2");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceId("dummy1", "dummy2");
		
		Mono<ListSpacesResponse> response2 = subject.retrieveSpaceId("dummy1", "dummy2");
		response2.subscribe();
		assertThat(response1).isNotEqualTo(response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveOrgId("dummy");
	}

	@Test
	public void testRetrieveAllApplicationIdsInSpace() {
		Mono<ListApplicationsResponse> response1 = subject.retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		
		Mono<ListApplicationsResponse> response2 = subject.retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
		response2.subscribe();
		assertThat(response1).isNotEqualTo(response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllApplicationIdsInSpace("dummy1", "dummy2");
	}

	@Test
	public void testRetrieveSpaceSummary() {
		Mono<GetSpaceSummaryResponse> response1 = subject.retrieveSpaceSummary("dummy");
		response1.subscribe();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceSummary("dummy");
		
		Mono<GetSpaceSummaryResponse> response2 = subject.retrieveSpaceSummary("dummy");
		response2.subscribe();
		assertThat(response1).isNotEqualTo(response2);
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveSpaceSummary("dummy");
	}

}
