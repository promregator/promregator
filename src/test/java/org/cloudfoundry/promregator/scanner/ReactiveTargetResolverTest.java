package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.cloudfoundry.promregator.config.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedReactiveTargetResolverSpringApplication.class)
@TestPropertySource(properties = "spring.cloud.config.enabled=false")
public class ReactiveTargetResolverTest {
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@AfterEach
	void resetCFAccessorMock() {
		Mockito.reset(this.cfAccessor);
	}

	@Autowired
	private TargetResolver targetResolver;
	
	@Autowired
	private CFAccessor cfAccessor;

	@Test
	void testFullyResolvedAlready() {
		
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals(t.getApplicationName(), rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppIds(Mockito.anySet());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveWebProcessesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveAllDomainsV3(Mockito.anyString());
	}
	
	@Test
	void testMissingApplicationName() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(4, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		rt = actualList.get(1);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp2", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppIds(Mockito.anySet());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveWebProcessesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveAllDomainsV3(Mockito.anyString());
	}

	@Test
	void testWithApplicationRegex() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationRegex(".*2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp2", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppIds(Mockito.anySet());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveWebProcessesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveAllDomainsV3(Mockito.anyString());
	}
	
	@Test
	void testWithApplicationRegexCaseInsensitiveIssue76() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationRegex("te.*App2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp2", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppIds(Mockito.anySet());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveWebProcessesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveAllDomainsV3(Mockito.anyString());
	}
	
	@Test
	void testEmpty() {
		
		List<Target> list = new LinkedList<>();
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertNotNull(actualList);
		Assertions.assertEquals(0, actualList.size());
	}

	@Test
	void testSummaryDoesnotExist() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace-summarydoesnotexist");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(0, actualList.size());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID_DOESNOTEXIST);
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppIds(Mockito.anySet());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveWebProcessesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveAllDomainsV3(Mockito.anyString());
	}
	
	@Test
	void testRetrieveAllApplicationIdsInSpaceThrowsException() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace-summaryexception");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(0, actualList.size());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID_EXCEPTION);
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveRoutesForAppIds(Mockito.anySet());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveWebProcessesForAppId(Mockito.anyString());
		Mockito.verify(this.cfAccessor, Mockito.times(0)).retrieveAllDomainsV3(Mockito.anyString());
	}
	
	@Test
	void testInvalidOrgNameToResolve() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("doesnotexist");
		t.setSpaceName("unittestspace");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals(t.getApplicationName(), rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
	}
	
	@Test
	void testExceptionOrgNameToResolve() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("exception");
		t.setSpaceName("unittestspace");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals(t.getApplicationName(), rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
	}
	
	@Test
	void testInvalidSpaceNameToResolve() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("doesnotexist");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals(t.getApplicationName(), rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
	}
	
	@Test
	void testExceptionSpaceNameToResolve() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("exception");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals(t.getApplicationName(), rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
	}

	@Test
	void testDistinctResolvedTargets() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationRegex("testapp.*");
		// Note that this will find all of testapp, testapp2 and testapp3
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		// this is causing the double-selection
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		// NB: The regex target (first one) overlaps with the already fully resolved target (second one)
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(3, actualList.size()); // and not 4!
		
		boolean testappFound = false;
		boolean testapp2Found = false;
		boolean testapp3Found = false;
		for (ResolvedTarget rt : actualList) {
			Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
			Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
			Assertions.assertEquals(t.getPath(), rt.getPath());
			Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
			
			if (rt.getApplicationName().equals("testapp2")) {
				testapp2Found = true;
			} else if (rt.getApplicationName().equals("testapp")) {
				if (testappFound) {
					// testApp was already found before; this would be a duplicate entry, which is what we want to avoid
					Assertions.fail("Duplicate entry for testapp returned");
				}
				testappFound = true;
			} else if (rt.getApplicationName().equals("testapp3")) {
				testapp3Found = true;
			} else {
				Assertions.fail("Unknown application name returned");
			}
		}
		
		Assertions.assertTrue(testappFound);
		Assertions.assertTrue(testapp2Found);
		Assertions.assertTrue(testapp3Found);
	}

	@Test
	void testMissingOrgName() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals("unittestorg", rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllOrgIdsV3();
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
	}

	@Test
	void testWithOrgRegex() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgRegex("unittest.*");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals("unittestorg", rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp2", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllOrgIdsV3();
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
	}
	
	@Test
	void testWithOrgRegexCaseInsensitive() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgRegex("unit.*TOrg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals("unittestorg", rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp2", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
	}
	
	@Test
	void testMissingSpaceName() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setApplicationName("testapp");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals("unittestorg", rt.getOrgName());
		Assertions.assertEquals("unittestspace", rt.getSpaceName());
		Assertions.assertEquals("testapp", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveSpaceIdsInOrgV3(CFAccessorMock.UNITTEST_ORG_UUID);
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
	}

	@Test
	void testWithSpaceRegex() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceRegex("unitte.*");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals("unittestorg", rt.getOrgName());
		Assertions.assertEquals("unittestspace", rt.getSpaceName());
		Assertions.assertEquals("testapp2", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveSpaceIdsInOrgV3(CFAccessorMock.UNITTEST_ORG_UUID);
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
	}
	
	@Test
	void testWithSpaceRegexCaseInsensitive() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceRegex("unit.*tSpace");
		t.setApplicationName("testapp2");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals("unittestorg", rt.getOrgName());
		Assertions.assertEquals("unittestspace", rt.getSpaceName());
		Assertions.assertEquals("testapp2", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveSpaceIdsInOrgV3(CFAccessorMock.UNITTEST_ORG_UUID);
		Mockito.verify(this.cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID);
	}
	
	@Test
	void testCorrectingCaseOnNamesIssue77() {
		
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("uniTtEstOrg");
		t.setSpaceName("unitteStspAce");
		t.setApplicationName("testApp");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals("unittestorg", rt.getOrgName());
		Assertions.assertEquals("unittestspace", rt.getSpaceName());
		Assertions.assertEquals("testapp", rt.getApplicationName());
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());

	}
	
	@Test
	void testInvalidOrgNameDoesNotRaiseExceptionIssue109() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("doesnotexist");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(0, actualList.size());
		
	}

	@Test
	void testInvalidSpaceNameDoesNotRaiseExceptionIssue109() {
		
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("doesnotexist");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assertions.assertEquals(0, actualList.size());
	}

	@Test
	void testWithAnnotationsUnexpectedValue() {
		List<Target> list = new LinkedList<>();

		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationRegex(".*2");
		t.setPath("path");
		t.setProtocol("https");
		t.setKubernetesAnnotations(true);
		list.add(t);
		// testapp2 has an invalid scrape value

		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);

		Assertions.assertEquals(0, actualList.size());
		Mockito.verify(this.cfAccessor, Mockito.times(2)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID,
																						   CFAccessorMock.UNITTEST_SPACE_UUID);
	}

	@Test
	void testWithAnnotations() {
		List<Target> list = new LinkedList<>();

		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationRegex(".*");
		t.setPath("path");
		t.setProtocol("https");
		t.setKubernetesAnnotations(true);
		list.add(t);

		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);

		Assertions.assertEquals(2, actualList.size());

		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("testapp", rt.getApplicationName());
		// Defaults pathing to config value
		Assertions.assertEquals(t.getPath(), rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());

		rt = actualList.get(1);
		Assertions.assertEquals(t, rt.getOriginalTarget());
		Assertions.assertEquals(t.getOrgName(), rt.getOrgName());
		Assertions.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assertions.assertEquals("internalapp", rt.getApplicationName());
		// Overrides pathing with annotations
		Assertions.assertEquals("/actuator/prometheus", rt.getPath());
		Assertions.assertEquals(t.getProtocol(), rt.getProtocol());
		Mockito.verify(this.cfAccessor, Mockito.times(5)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID,
																						   CFAccessorMock.UNITTEST_SPACE_UUID);
	}
}
