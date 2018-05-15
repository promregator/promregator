package org.cloudfoundry.promregator.scanner;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.config.Target;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.prometheus.client.CollectorRegistry;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedMassReactiveAppInstanceScannerSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class MassReactiveAppInstanceScannerTest {

	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@AfterClass
	public static void releaseInternalMetrics() {
		CollectorRegistry.defaultRegistry.clear();
	}
	
	@Test
	public void testPerformance() {
		List<Target> targets = new LinkedList<>();
		
		Target t = null;
		
		final int numberOfApps = 10000;
		
		for (int i = 0;i<numberOfApps;i++) {
			t = new Target();
			t.setOrgName("unittestorg");
			t.setSpaceName("unittestspace");
			t.setApplicationName("testapp"+i);
			t.setPath("/testpath");
			t.setProtocol("http");
			targets.add(t);
		}
		
		Instant start = Instant.now();
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets);
		
		Instant stop = Instant.now();
		
		Assert.assertEquals(numberOfApps*10, result.size());
		
		// test to be faster than 6 seconds
		Duration d = Duration.between(start, stop);
		Assert.assertTrue(d.minusSeconds(6).isNegative());
	}

}
