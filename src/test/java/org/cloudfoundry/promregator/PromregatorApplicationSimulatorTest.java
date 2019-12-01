package org.cloudfoundry.promregator;

import java.util.List;

import javax.validation.constraints.Null;

import org.cloudfoundry.promregator.cfaccessor.CFAccessorSimulator;
import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.scanner.Instance;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { PromregatorApplication.class })
// Note that adding @ComponentScan(excludeFilters ... ) here does not make sense
// as PromregatorApplication will anyhow override it!

// Hint: activate the simulation profile, which is defined in src/test/resources/application.yml
@ActiveProfiles(profiles = {"simulation"})

@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class PromregatorApplicationSimulatorTest {

	@Test
	public void contextLoads() {
	}
	
	@Autowired
	private CFDiscoverer cfDiscoverer;
	
	@Test
	public void testDiscoveryWorks() {
		@Null
		List<Instance> actual = this.cfDiscoverer.discover(null, null);
		Assert.assertEquals(200, actual.size());
	}
	
	@Test
	public void testSingleInstance() {
		@Null
		List<Instance> actual = this.cfDiscoverer.discover(appId -> appId.equals(CFAccessorSimulator.APP_UUID_PREFIX+"100"), 
				instance -> (CFAccessorSimulator.APP_UUID_PREFIX+"100:1").equals(instance.getInstanceId()));
		Assert.assertEquals(1, actual.size());
	}

	@AfterClass
	public static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
}
