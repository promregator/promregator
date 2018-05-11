package org.cloudfoundry.promregator;

import org.cloudfoundry.promregator.endpoint.TestableMetricsEndpoint;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import io.prometheus.client.CollectorRegistry;

@RunWith(SpringRunner.class)
@BootstrapWith(value=SpringBootTestContextBootstrapper.class)
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = { TypeExcludeFilter.class }),
		@Filter(type = FilterType.CUSTOM, classes = { AutoConfigurationExcludeFilter.class }),
		@Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {  TestableMetricsEndpoint.class })
		// NB: TestableMetricsEndpoint would break here everything
})
@TestPropertySource(locations="default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
public class PromregatorApplicationTest {

	@Test
	public void contextLoads() {
	}

	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
}
