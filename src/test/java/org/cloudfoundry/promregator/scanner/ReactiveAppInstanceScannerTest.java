package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
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
@SpringBootTest(classes = MockedReactiveAppInstanceScannerSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class ReactiveAppInstanceScannerTest {

	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@AfterClass
	public static void cleanupEnvironment() {
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
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets);
		
		boolean testapp1_instance1 = false;
		boolean testapp1_instance2 = false;
		boolean testapp2_instance1 = false;
		
		for (Instance instance : result) {
			String instanceId = instance.getInstanceId();
			
			if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":0")) {
				testapp1_instance1 = true;
				Assert.assertEquals("http://hostapp1.shared.domain.example.org/testpath1", instance.getAccessUrl());
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":1")) {
				testapp1_instance2 = true;
				Assert.assertEquals("http://hostapp1.shared.domain.example.org/testpath1", instance.getAccessUrl());
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP2_UUID+":0")) {
				testapp2_instance1 = true;
				Assert.assertEquals("https://hostapp2.shared.domain.example.org/additionalPath/testpath2", instance.getAccessUrl());
			}
		}
		Assert.assertTrue(testapp1_instance1);
		Assert.assertTrue(testapp1_instance2);
		Assert.assertTrue(testapp2_instance1);
	}

}
