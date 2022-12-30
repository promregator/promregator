package org.cloudfoundry.promregator.scanner;

import static org.cloudfoundry.promregator.cfaccessor.CFAccessorMassMock.UNITTEST_APP_UUID_PREFIX;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedMassReactiveAppInstanceScannerSpringApplication.class)
@TestPropertySource(locations="default.properties")
class MassReactiveAppInstanceScannerTest {

	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@AfterAll
	static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testPerformance() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = null;
		
		final int numberOfApps = 100;
		
		final Target emptyTarget = new Target();
		for (int i = 0;i<numberOfApps;i++) {
			t = new ResolvedTarget();
			t.setOrgName("unittestorg");
			t.setSpaceName("unittestspace");
			t.setApplicationName("testapp"+i);
			t.setApplicationId(UNITTEST_APP_UUID_PREFIX+i);
			t.setPath("/testpath");
			t.setProtocol("http");
			t.setOriginalTarget(emptyTarget);
			targets.add(t);
		}
		
		Instant start = Instant.now();
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		Assertions.assertNotNull(result);
		
		Instant stop = Instant.now();
		
		Assertions.assertEquals(numberOfApps*10, result.size());
		
		// test to be faster than 6 seconds
		Duration d = Duration.between(start, stop);
		Assertions.assertTrue(d.minusSeconds(6).isNegative());
	}

	@Test
	void testPerformanceWithInstanceFilter() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = null;
		
		final int numberOfApps = 100;
		
		final Target emptyTarget = new Target();
		for (int i = 0;i<numberOfApps;i++) {
			t = new ResolvedTarget();
			t.setOrgName("unittestorg");
			t.setSpaceName("unittestspace");
			t.setApplicationName("testapp"+i);
			t.setApplicationId(UNITTEST_APP_UUID_PREFIX+i);
			t.setPath("/testpath");
			t.setProtocol("http");
			t.setOriginalTarget(emptyTarget);
			targets.add(t);
		}
		
		Instant start = Instant.now();
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, instance -> {
			// filters out all instances, but only the instance "0" is kept
			
			if (instance.getInstanceId().endsWith(":0"))
				return true;
			
			return false;
		});
		Assertions.assertNotNull(result);
		
		Instant stop = Instant.now();
		
		Assertions.assertEquals(numberOfApps*1, result.size());
		
		// test to be faster than 6 seconds
		Duration d = Duration.between(start, stop);
		Assertions.assertTrue(d.minusSeconds(6).isNegative());
	}

	@Test
	void testPerformanceWithApplicationIdAndInstanceFilter() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = null;
		
		final int numberOfApps = 100;
		
		final Target emptyTarget = new Target();
		for (int i = 0;i<numberOfApps;i++) {
			t = new ResolvedTarget();
			t.setOrgName("unittestorg");
			t.setSpaceName("unittestspace");
			t.setApplicationName("testapp"+i);
			t.setPath("/testpath");
			t.setProtocol("http");
			t.setApplicationId(UNITTEST_APP_UUID_PREFIX+i);
			t.setOriginalTarget(emptyTarget);
			targets.add(t);
		}
		
		Instant start = Instant.now();
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, applicationId -> {
			// filters out all applications,except for one
			if (applicationId.equals(UNITTEST_APP_UUID_PREFIX+"0"))
				return true;
			
			return false;
		}, instance -> {
			// filters out all instances, but only the instance "0" is kept
			
			if (instance.getInstanceId().endsWith(":0"))
				return true;
			
			return false;
		});
		Assertions.assertNotNull(result);
		
		Instant stop = Instant.now();
		
		Assertions.assertEquals(1, result.size());
		
		// test to be faster than 3 seconds
		Duration d = Duration.between(start, stop);
		Assertions.assertTrue(d.minusSeconds(3).isNegative());
	}
	
	@Test
	void testPathsAreNotMixedUpIssue59() {
		// see also https://github.com/promregator/promregator/issues/59#issuecomment-399037194
		
		final int numberOfApps = 100;
		
		List<ResolvedTarget> targets = new LinkedList<>();

		ResolvedTarget t = null;
		
		final Target emptyTarget = new Target();
		for (int i = 0;i<numberOfApps;i++) {
			t = new ResolvedTarget();
			t.setOrgName("unittestorg");
			t.setSpaceName("unittestspace");
			t.setApplicationName("testapp"+i);
			t.setApplicationId(UNITTEST_APP_UUID_PREFIX+i);
			t.setPath("/testpath"+i);
			t.setProtocol("http");
			t.setOriginalTarget(emptyTarget);
			targets.add(t);
		}
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		Assertions.assertNotNull(result);
		
		Assertions.assertEquals(numberOfApps*10, result.size());

		for (Instance instance : result) {
			String targetNumber = instance.getTarget().getApplicationName().substring(7);
			
			Assertions.assertEquals("/testpath"+targetNumber, instance.getTarget().getPath());
			Assertions.assertTrue(instance.getAccessUrl().endsWith(targetNumber));
		}
	}
}
