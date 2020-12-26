package org.cloudfoundry.promregator.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.promregator.cfaccessor.CFAccessorMock.UNITTEST_APP1_UUID;
import static org.cloudfoundry.promregator.cfaccessor.CFAccessorMock.UNITTEST_APP2_UUID;

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
	public static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	public void testStraightForward() {
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
	public void testWithPrefiltering() {
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
	public void testWithWrongCaseIssue76() {
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
	
	@Test
	public void testEmptyResponseOnOrg() {
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
		t.setOrgName("doesnotexist");
		t.setSpaceName("shouldneverbeused");
		t.setApplicationName("shouldneverbeused");
		t.setPath("/shouldneverbeused");
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
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	public void testEmptyResponseOnSpace() {
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
		t.setSpaceName("doesnotexist");
		t.setApplicationName("shouldneverbeused");
		t.setPath("/shouldneverbeused");
		t.setOriginalTarget(emptyTarget);
		t.setProtocol("https");
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
	public void testEmptyResponseOnApplicationId() {
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
		t.setApplicationName("doesnotexist");
		t.setPath("/shouldneverbeused");
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
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");

	}
	
	@Test
	public void testEmptyResponseOnSummary() {
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
		t.setSpaceName("unittestspace-summarydoesnotexist");
		t.setApplicationName("testapp");
		t.setPath("/shouldneverbeused");
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
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	public void testExceptionOnOrg() {
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
		t.setOrgName("exception");
		t.setSpaceName("shouldneverbeused");
		t.setApplicationName("shouldneverbeused");
		t.setPath("/shouldneverbeused");
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
	public void testExceptionOnSpace() {
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
		t.setSpaceName("exception");
		t.setApplicationName("shouldneverbeused");
		t.setPath("/shouldneverbeused");
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
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	public void testExceptionOnApplicationId() {
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
		t.setApplicationName("exception");
		t.setPath("/shouldneverbeused");
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
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}
	
	@Test
	public void testExceptionOnSummary() {
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
		t.setSpaceName("unittestspace-summaryexception");
		t.setApplicationName("testapp");
		t.setPath("/shouldneverbeused");
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
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":0") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP1_UUID+":1") )
				.extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1");

		assertThat(result).filteredOn( instance -> instance.getInstanceId().equals(UNITTEST_APP2_UUID+":0") )
				.extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2");
	}

	@Test
	public void testMatchPreferredRouteRegex() {
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
	public void testMatchPreferredRouteRegexNotMatched() {
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
}
