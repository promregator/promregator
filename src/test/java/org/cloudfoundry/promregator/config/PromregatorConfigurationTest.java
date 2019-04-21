package org.cloudfoundry.promregator.config;

import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;


public class PromregatorConfigurationTest {

	@Test
	public void testUnrollingSimple() {
		PromregatorConfiguration subject = new PromregatorConfiguration();
		
		// First Target - With applications
		List<CommonConfigurationApplication> applications = new LinkedList<>();
		CommonConfigurationApplication cca = new CommonConfigurationApplication();
		cca.setApplicationName("appName1");
		cca.setPath("/metrics");
		applications.add(cca);
		
		cca = new CommonConfigurationApplication();
		cca.setApplicationName("appName2");
		cca.setPath("/metrics2");
		applications.add(cca);
		
		ConfigurationTarget t = new ConfigurationTarget();
		t.setOrgName("orgname");
		t.setSpaceName("spaceName");
		t.setApplications(applications);

		List<ConfigurationTarget> targets = new LinkedList<>();
		targets.add(t);

		// Second target (simple)
		
		t = new ConfigurationTarget();
		t.setOrgName("orgname");
		t.setSpaceName("spaceName");
		t.setApplicationName("appName3");
		t.setPath("/prometheus");
		targets.add(t);
		
		// Start processing
		
		subject.setTargets(targets);
		List<Target> result = subject.getTargets();
		
		Assert.assertEquals(3, result.size());
		
		boolean app1 = false;
		boolean app2 = false;
		boolean app3 = false;
		for (Target target : result) {
			Assert.assertEquals("orgname", target.getOrgName());
			Assert.assertEquals("spaceName", target.getSpaceName());
			
			if (target.getApplicationName().equals("appName1")) {
				app1 = true;
				Assert.assertEquals("/metrics", target.getPath());
			} else if (target.getApplicationName().equals("appName2")) {
				app2 = true;
				Assert.assertEquals("/metrics2", target.getPath());
			} else if (target.getApplicationName().equals("appName3")) {
				app3 = true;
				Assert.assertEquals("/prometheus", target.getPath());
			} else {
				Assert.fail("Unknown application name");
			}
		}
		
		Assert.assertTrue(app1);
		Assert.assertTrue(app2);
		Assert.assertTrue(app3);
	}
	
}
