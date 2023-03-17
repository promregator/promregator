package org.cloudfoundry.promregator.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.messagebus.MessageBus;
import org.cloudfoundry.promregator.messagebus.MessageBusTopic;
import org.cloudfoundry.promregator.messagebus.MessageSubscriber;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = CFDiscovererTestSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class CFMultiDiscovererTest implements MessageSubscriber {
	
	@AfterAll
	public static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Autowired
	private CFMultiDiscoverer cfDiscoverer;

	@Autowired
	private TargetResolver targetResolver;

	@Autowired
	private Clock clock;
	
	@Autowired
	private MessageBus messageBus;
	
	private List<Instance> removerTriggerForInstances = new LinkedList<>();
	
	@Override
	public void receiveMessage(String topic, Object message) {
		Assertions.assertEquals(MessageBusTopic.DISCOVERER_INSTANCE_REMOVED, topic);
		
		Instance instance = (Instance) message;
		
		this.removerTriggerForInstances.add(instance);
	}

	@Test
	void testDiscoverWithCleanup() throws InterruptedException {
		this.messageBus.subscribe(MessageBusTopic.DISCOVERER_INSTANCE_REMOVED, this);
		
		List<ResolvedTarget> resolvedTargets = new ArrayList<>();
		ResolvedTarget aTarget = new ResolvedTarget();
		aTarget.setOrgName("unittestorg");
		aTarget.setSpaceName("unittestspace");
		aTarget.setApplicationName("testapp");
		aTarget.setApplicationId(CFAccessorMock.UNITTEST_APP1_UUID);
		aTarget.setProtocol("https");
		aTarget.setPath("/metrics");
		aTarget.setOriginalTarget(new Target());
		resolvedTargets.add(aTarget);
		when(targetResolver.resolveTargets(any())).thenReturn(resolvedTargets);

		List<Instance> result = this.cfDiscoverer.discover(null, null);
		
		Assertions.assertEquals(2, result.size());
		
		boolean testapp1_instance1 = false;
		boolean testapp1_instance2 = false;
		
		Instance i1 = null;
		Instance i2 = null;
		
		for (Instance instance : result) {
			String instanceId = instance.getInstanceId();
			
			if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":0")) {
				testapp1_instance1 = true;
				Assertions.assertEquals("https://hostapp1.shared.domain.example.org/metrics", instance.getAccessUrl());
				i1 = instance;
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP1_UUID+":1")) {
				testapp1_instance2 = true;
				Assertions.assertEquals("https://hostapp1.shared.domain.example.org/metrics", instance.getAccessUrl());
				i2 = instance;
			} else if (instanceId.equals(CFAccessorMock.UNITTEST_APP2_UUID+":0")) {
				Assertions.fail("Should not have been returned");
			}
		}
		Assertions.assertTrue(testapp1_instance1);
		Assertions.assertTrue(testapp1_instance2);
		
		Assertions.assertNotNull(i1);
		Assertions.assertNotNull(i2);
		
		Assertions.assertTrue(this.cfDiscoverer.isInstanceRegistered(i1));
		Assertions.assertTrue(this.cfDiscoverer.isInstanceRegistered(i2));
		Assertions.assertTrue(this.removerTriggerForInstances.isEmpty());
		
		// early cleaning does not change anything
		this.cfDiscoverer.cleanup();
		Assertions.assertTrue(this.cfDiscoverer.isInstanceRegistered(i1));
		Assertions.assertTrue(this.cfDiscoverer.isInstanceRegistered(i2));
		
		// Wait a little to allow JMX do its job... (if it really did something)
		for (int i = 0;i<10;i++) {
			if (!this.removerTriggerForInstances.isEmpty()) {
				Thread.sleep(100);
				continue;
			}
			Assertions.assertTrue(this.removerTriggerForInstances.isEmpty());
		}
		
		// later cleaning does...
		this.cfDiscoverer.setClock(Clock.offset(this.clock, Duration.ofMinutes(10)));
		
		this.cfDiscoverer.cleanup();
		
		Assertions.assertFalse(this.cfDiscoverer.isInstanceRegistered(i1));
		Assertions.assertFalse(this.cfDiscoverer.isInstanceRegistered(i2));
		
		for (int i = 0;i<10;i++) {
			if (this.removerTriggerForInstances.isEmpty()) {
				Thread.sleep(400);
				continue;
			}
			Assertions.assertEquals(2, this.removerTriggerForInstances.size());
		}
	}


}
