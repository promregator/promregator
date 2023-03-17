package org.cloudfoundry.promregator.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.promregator.cfaccessor.CFAccessorMock.UNITTEST_APP1_UUID;
import static org.cloudfoundry.promregator.cfaccessor.CFAccessorMock.UNITTEST_APP2_UUID;
import static org.cloudfoundry.promregator.cfaccessor.CFAccessorMock.UNITTEST_APP3_UUID;
import static org.cloudfoundry.promregator.cfaccessor.CFAccessorMock.UNITTEST_APP_INTERNAL_UUID;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedReactiveAppInstanceScannerSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class ReactiveAppInstanceScannerTest {

	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@AfterAll
	static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testStraightForward() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP1_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setApplicationId(UNITTEST_APP2_UUID);
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	void testWithPrefiltering() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP1_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setApplicationId(UNITTEST_APP2_UUID);
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, instance -> {
			if (instance.getInstanceId().startsWith(UNITTEST_APP1_UUID))
				// the instances of app1 are being filtered away
				return false;

			return true;
		});
		
		assertThat(result).as("should have been filtered").extracting("instanceId").doesNotContain(UNITTEST_APP1_UUID+":0");
		assertThat(result).as("should have been filtered").extracting("instanceId").doesNotContain(UNITTEST_APP1_UUID+":1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}

	@Test
	void testWithWrongCaseIssue76() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP1_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testApp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setApplicationId(UNITTEST_APP2_UUID);
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	private List<ResolvedTarget> setupTargets(String orgName, String spaceName, String applicationName, String path) {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP1_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName(orgName);
		t.setSpaceName(spaceName);
		t.setApplicationName(applicationName);
		t.setPath(path);
		t.setProtocol("https");
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setApplicationId(UNITTEST_APP2_UUID);
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		return targets;

	}
	
	@Test
	void testEmptyResponseOnOrg() {
		List<ResolvedTarget> targets = this.setupTargets("doesnotexist", "shouldneverbeused", "shouldneverbeused", "/shouldneverbeused");

		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	void testEmptyResponseOnSpace() {
		List<ResolvedTarget> targets = this.setupTargets("unittestorg", "doestnotexist", "shouldneverbeused", "/shouldneverbeused");
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	
	@Test
	void testEmptyResponseOnApplicationId() {
		List<ResolvedTarget> targets = this.setupTargets("unittestorg", "unittestorg", "doestnotexist", "/shouldneverbeused");
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");

	}
	
	@Test
	void testEmptyResponseOnSummary() {
		List<ResolvedTarget> targets = this.setupTargets("unittestorg", "unittestspace-summarydoesnotexist", "testapp", "/shouldneverbeused");
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	void testExceptionOnOrg() {
		List<ResolvedTarget> targets = this.setupTargets("exception", "shouldneverbeused", "shouldneverbeused", "/shouldneverbeused");
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).as("targets with exceptions are ignored").extracting("target.applicationName").doesNotContain("shouldneverbeused");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}

	@Test
	void testExceptionOnSpace() {
		List<ResolvedTarget> targets = this.setupTargets("unittestorg", "exception", "shouldneverbeused", "/shouldneverbeused");
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	void testExceptionOnApplicationId() {
		List<ResolvedTarget> targets = this.setupTargets("unittestorg", "unittestspace", "exception", "/shouldneverbeused");
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	void testExceptionOnSummary() {
		List<ResolvedTarget> targets = this.setupTargets("unittestorg", "unittestspace-summaryexception", "testapp", "/shouldneverbeused");
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}

	@Test
	void testMatchPreferredRouteRegex() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP1_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setApplicationId(UNITTEST_APP2_UUID);
		
		Target origTarget = new Target();
		String[] preferredRouteRegex = { ".*additionalSubdomain.*" };
		origTarget.setPreferredRouteRegex(Arrays.asList(preferredRouteRegex));
		
		t.setOriginalTarget(origTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.additionalSubdomain.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	void testMatchPreferredRouteRegexNotMatched() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP1_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setApplicationId(UNITTEST_APP2_UUID);
		
		Target origTarget = new Target();
		String[] preferredRouteRegex = { ".*notMatched.*" };
		origTarget.setPreferredRouteRegex(Arrays.asList(preferredRouteRegex));
		
		t.setOriginalTarget(origTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}


	@Test
	void testStraightForwardInternalRoute() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("internalapp");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP_INTERNAL_UUID);
		final Target emptyTarget = new Target();
		emptyTarget.setInternalRoutePort(9090);
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
					
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":0") )
			.extracting("internal").containsOnly(true);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":0") )
			.extracting("accessUrl").containsOnly("http://0.internal-app.apps.internal:9090/metrics");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":1") )
			.extracting("accessUrl").containsOnly("http://1.internal-app.apps.internal:9090/metrics");
	}

	@Test
	void testInternalRouteWithNoPortDefinedInTarget() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("internalapp");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP_INTERNAL_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":0") )
			.extracting("internal").containsOnly(true);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":0") )
			.extracting("accessUrl").containsOnly("http://0.internal-app.apps.internal:8080/metrics");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":1") )
			.extracting("accessUrl").containsOnly("http://1.internal-app.apps.internal:8080/metrics");
	}
	
	@Test
	void testTargetWithoutRoute() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp3");
		t.setPath("/testpath1");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP3_UUID);
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
		assertThat(result).isEmpty();
		// in particular, we should not dump/crash
	}

	@Test
	void testTargetWithOverrideRouteAndPath() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("internalapp");
		t.setProtocol("http");
		t.setApplicationId(UNITTEST_APP_INTERNAL_UUID);
		final Target targetWithExplicitAccessUrl = new Target();
		targetWithExplicitAccessUrl.setOverrideRouteAndPath("someRouteAndPath.com");
		t.setOriginalTarget(targetWithExplicitAccessUrl);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
		assertThat(result).isNotEmpty();

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":0") )
			.extracting("internal").containsOnly(true);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":0") )
			.extracting("accessUrl").containsOnly("http://someRouteAndPath.com/metrics");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP_INTERNAL_UUID+":1") )
			.extracting("accessUrl").containsOnly("http://someRouteAndPath.com/metrics");
	}

}
