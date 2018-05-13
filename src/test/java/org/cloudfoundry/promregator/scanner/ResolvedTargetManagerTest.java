package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Duration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ResolvedTargetManagerSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class ResolvedTargetManagerTest {
	@Autowired
	private ResolvedTargetManager subject;
	
	@Autowired
	private Clock clock;
	
	@After
	public void resetClock() {
		this.subject.setClock(this.clock);
		
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
	public void testCleanup() {
		ResolvedTarget rt = new ResolvedTarget();
		rt.setOrgName("orgname");
		rt.setSpaceName("spacename");
		rt.setApplicationName("applicationname");
		rt.setPath("/path");
		rt.setProtocol("https");
		
		this.subject.registerResolvedTarget(rt);
		
		this.subject.setClock(Clock.offset(this.clock, Duration.ofSeconds(305)));
		this.subject.cleanup();
		
		Assert.assertTrue(this.subject.isEmpty());
	}

}
