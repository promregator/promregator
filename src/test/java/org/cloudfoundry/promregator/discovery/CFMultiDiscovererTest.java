package org.cloudfoundry.promregator.discovery;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = CFDiscovererTestSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class CFMultiDiscovererTest {
	
	@AfterClass
	public static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Autowired
	private CFMultiDiscoverer cfDiscoverer;
	
	@Autowired
	private Clock clock;
	
	private List<Instance> removerTriggerForInstances = new LinkedList<>();
	
	@JmsListener(destination=MessageBusDestination.DISCOVERER_INSTANCE_REMOVED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void receiver(Instance instance) {
		this.removerTriggerForInstances.add(instance);
	}
	
	@Test
	public void testDiscoverWithCleanup() throws InterruptedException {
		List<Instance> result = this.cfDiscoverer.discover(null, null);
		
		Assert.assertEquals(2, result.size());
		
		boolean testapp1_instance1 = false;
		boolean testapp1_instance2 = false;
		
		Instance i1 = null;
		Instance i2 = null;
		
		for (Instance instance : result) {
			String instanceId = instance.getInstanceId();
			
			if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":0")) {
				testapp1_instance1 = true;
				Assert.assertEquals("https://hostapp1.shared.domain.example.org/metrics", instance.getAccessUrl());
				i1 = instance;
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":1")) {
				testapp1_instance2 = true;
				Assert.assertEquals("https://hostapp1.shared.domain.example.org/metrics", instance.getAccessUrl());
				i2 = instance;
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP2_UUID+":0")) {
				Assert.fail("Should not have been returned");
			}
		}
		Assert.assertTrue(testapp1_instance1);
		Assert.assertTrue(testapp1_instance2);
		
		Assert.assertNotNull(i1);
		Assert.assertNotNull(i2);
		
		Assert.assertTrue(this.cfDiscoverer.isInstanceRegistered(i1));
		Assert.assertTrue(this.cfDiscoverer.isInstanceRegistered(i2));
		Assert.assertTrue(this.removerTriggerForInstances.isEmpty());
		
		// early cleaning does not change anything
		this.cfDiscoverer.cleanup();
		Assert.assertTrue(this.cfDiscoverer.isInstanceRegistered(i1));
		Assert.assertTrue(this.cfDiscoverer.isInstanceRegistered(i2));
		
		// Wait a little to allow JMX do its job... (if it really did something)
		for (int i = 0;i<10;i++) {
			if (!this.removerTriggerForInstances.isEmpty()) {
				Thread.sleep(100);
				continue;
			}
			Assert.assertTrue(this.removerTriggerForInstances.isEmpty());
		}
		
		// later cleaning does...
		this.cfDiscoverer.setClock(Clock.offset(this.clock, Duration.ofMinutes(10)));
		
		this.cfDiscoverer.cleanup();
		
		Assert.assertFalse(this.cfDiscoverer.isInstanceRegistered(i1));
		Assert.assertFalse(this.cfDiscoverer.isInstanceRegistered(i2));
		
		for (int i = 0;i<10;i++) {
			if (this.removerTriggerForInstances.size() == 0) {
				Thread.sleep(400);
				continue;
			}
			Assert.assertEquals(2, this.removerTriggerForInstances.size());
		}
	}

}
