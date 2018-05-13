package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Duration;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.prometheus.client.CollectorRegistry;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ResolvedTargetManagerSpringApplication.class)
@TestPropertySource(locations="default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class ResolvedTargetManagerTest {
	@Autowired
	private ResolvedTargetManager subject;
	
	@Autowired
	private Clock clock;
	
	@Autowired
	private TestableResolvedTargetManagerReceiver receiver;
	
	@After
	public void resetClock() {
		this.subject.setClock(this.clock);
	}
	
	@AfterClass
	public static void releaseInternalMetrics() {
		CollectorRegistry.defaultRegistry.clear();
	}
	
	@Test
	public void testStraightForward() {
		ResolvedTarget rt = new ResolvedTarget();
		rt.setOrgName("orgname");
		rt.setSpaceName("spacename");
		rt.setApplicationName("applicationname");
		rt.setPath("/path");
		rt.setProtocol("https");
		
		this.subject.registerResolvedTarget(rt);
		
		this.subject.deregisterResolvedTarget(rt);
	}
	
	@Test
	public void testCleanup() throws InterruptedException {
		ResolvedTarget rt = new ResolvedTarget();
		rt.setOrgName("orgname");
		rt.setSpaceName("spacename");
		rt.setApplicationName("applicationname");
		rt.setPath("/path");
		rt.setProtocol("https");
		
		this.subject.registerResolvedTarget(rt);
		
		this.subject.setClock(Clock.offset(this.clock, Duration.ofSeconds(305)));
		this.subject.cleanup();

		Thread.sleep(100); // wait a little to allow JMS propagate the event
		
		Assert.assertEquals(rt, this.receiver.getLastRt());
		
		Assert.assertTrue(this.subject.isEmpty());
	}
	
	@Test
	public void testTopicCommunicationDeregister() throws InterruptedException {
		ResolvedTarget rt = new ResolvedTarget();
		rt.setOrgName("orgname");
		rt.setSpaceName("spacename");
		rt.setApplicationName("applicationname");
		rt.setPath("/path");
		rt.setProtocol("https");
		
		this.subject.registerResolvedTarget(rt);
		
		this.subject.deregisterResolvedTarget(rt);
		
		Thread.sleep(100); // wait a little to allow JMS propagate the event
		
		Assert.assertEquals(rt, this.receiver.getLastRt());
	}

}
