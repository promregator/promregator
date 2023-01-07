package org.cloudfoundry.promregator.cfaccessor;

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

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = CFAccessorCacheCaffeineSpringApplication.class)
@TestPropertySource(locations="../default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class CFAccessorCacheCaffeineInvalidationTest {
	
	/*
	 * Warning! Do not try to merge with CFAccessorCacheCaffeineTest
	 * These tests here require that we have a clean counting state in
	 * Mockito, which is hard to achieve, if we have the tests merged.
	 * If they are separate, this is quite trivial...
	 */

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
		this.subject.invalidateCacheRoute();
		this.subject.invalidateCacheProcess();
	}
	
	@AfterAll
	public static void runCleanup() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testInvalidateCacheApplications() {
		subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		
		subject.invalidateCacheApplication();

		subject.retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllApplicationsInSpaceV3("dummy1", "dummy2");
	}

	@Test
	void testInvalidateCacheSpace() {
		subject.retrieveSpaceIdV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdV3("dummy1", "dummy2");
		
		subject.invalidateCacheSpace();
		
		subject.retrieveSpaceIdV3("dummy1", "dummy2");
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveSpaceIdV3("dummy1", "dummy2");
	}

	@Test
	void testInvalidateCacheSpaceInOrgCache() {
		subject.retrieveSpaceIdsInOrgV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveSpaceIdsInOrgV3("dummy");
		
		subject.invalidateCacheSpace();
		
		subject.retrieveSpaceIdsInOrgV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveSpaceIdsInOrgV3("dummy");
	}
	
	@Test
	void testInvalidateCacheOrg() {
		subject.retrieveOrgIdV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveOrgIdV3("dummy");
		
		subject.invalidateCacheOrg();
		
		subject.retrieveOrgIdV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveOrgIdV3("dummy");
	}

	@Test
	void testInvalidateCacheOrgAllOrgCache() {
		subject.retrieveAllOrgIdsV3();
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllOrgIdsV3();
		
		subject.invalidateCacheOrg();
		
		subject.retrieveAllOrgIdsV3();
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllOrgIdsV3();
	}

	
	@Test
	void testInvalidateCacheDomain() {
		subject.retrieveAllDomainsV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(1)).retrieveAllDomainsV3("dummy");
		
		subject.invalidateCacheDomain();
		
		subject.retrieveAllDomainsV3("dummy");
		Mockito.verify(this.parentMock, Mockito.times(2)).retrieveAllDomainsV3("dummy");
	}
	
	@Test
	void testInvalidateCacheRoute() {
		subject.retrieveRoutesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.timeout(500).times(1)).retrieveRoutesForAppIds(Mockito.anySet());
		
		subject.invalidateCacheRoute();
		
		subject.retrieveRoutesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.timeout(500).times(2)).retrieveRoutesForAppIds(Mockito.anySet());
	}

	@Test
	void testInvalidateCacheProcess() {
		subject.retrieveWebProcessesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.timeout(500).times(1)).retrieveWebProcessesForAppIds(Mockito.anySet());
		
		subject.invalidateCacheProcess();
		
		subject.retrieveWebProcessesForAppId("dummy");
		Mockito.verify(this.parentMock, Mockito.timeout(500).times(2)).retrieveWebProcessesForAppIds(Mockito.anySet());
	}

	
}
