package org.cloudfoundry.promregator;

import java.util.List;

import javax.validation.constraints.Null;

import org.cloudfoundry.promregator.cfaccessor.CFAccessorSimulator;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.endpoint.TestableMetricsEndpoint;
import org.cloudfoundry.promregator.scanner.Instance;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@BootstrapWith(value=SpringBootTestContextBootstrapper.class)
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = { TypeExcludeFilter.class }),
		@Filter(type = FilterType.CUSTOM, classes = { AutoConfigurationExcludeFilter.class }),
		@Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {  TestableMetricsEndpoint.class })
		// NB: TestableMetricsEndpoint would break here everything
})
@TestPropertySource(locations="simulator.yaml")
@ContextConfiguration(initializers = ConfigFileApplicationContextInitializer.class)
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class PromregatorApplicationSimulatorTest {

	@Test
	public void contextLoads() {
	}
	
	@Autowired
	private CFMultiDiscoverer cfDiscoverer;
	
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
				instance -> {
					return (CFAccessorSimulator.APP_UUID_PREFIX+"100:1").equals(instance.getInstanceId());
				});
		Assert.assertEquals(1, actual.size());
	}

	@AfterClass
	public static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
}
