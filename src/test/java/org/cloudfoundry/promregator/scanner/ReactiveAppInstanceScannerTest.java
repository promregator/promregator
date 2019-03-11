package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.cloudfoundry.promregator.config.Target;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedReactiveAppInstanceScannerSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class ReactiveAppInstanceScannerTest {

	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@AfterClass
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
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	@Test
	public void testWithPrefiltering() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, instance -> {
			if (instance.getInstanceId().startsWith(CFAccessorMock.UNITTEST_APP1_UUID))
				// the instances of app1 are being filtered away
				return false;
			
			return true;
		});
		
		boolean testapp2_instance1 = false;
		
		for (Instance instance : result) {
			String instanceId = instance.getInstanceId();
			
			if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":0")) {
				Assert.fail("should have been filtered");
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":1")) {
				Assert.fail("should have been filtered");
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP2_UUID+":0")) {
				testapp2_instance1 = true;
				Assert.assertEquals("https://hostapp2.shared.domain.example.org/additionalPath/testpath2", instance.getAccessUrl());
			}
		}
		Assert.assertTrue(testapp2_instance1);
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
		final Target emptyTarget = new Target();
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testApp2");
		t.setPath("/testpath2");
		t.setProtocol("https");
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	@Test
	public void testEmptyResponseOnOrg() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	@Test
	public void testEmptyResponseOnSpace() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	
	@Test
	public void testEmptyResponseOnApplicationId() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	@Test
	public void testEmptyResponseOnSummary() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	@Test
	public void testExceptionOnOrg() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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

	@Test
	public void testExceptionOnSpace() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	@Test
	public void testExceptionOnApplicationId() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
	
	@Test
	public void testExceptionOnSummary() {
		List<ResolvedTarget> targets = new LinkedList<>();
		
		ResolvedTarget t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("/testpath1");
		t.setProtocol("http");
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
		t.setOriginalTarget(emptyTarget);
		targets.add(t);
		
		List<Instance> result = this.appInstanceScanner.determineInstancesFromTargets(targets, null, null);
		
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
