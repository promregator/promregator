package org.cloudfoundry.promregator;

import static org.assertj.core.api.Assertions.assertThat;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.endpoint.TestableMetricsEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootstrapWith(value=SpringBootTestContextBootstrapper.class)
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = { TypeExcludeFilter.class }),
		@Filter(type = FilterType.CUSTOM, classes = { AutoConfigurationExcludeFilter.class }),
		@Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {  TestableMetricsEndpoint.class })
		// NB: TestableMetricsEndpoint would break here everything
})
@TestPropertySource(locations="default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
class PromregatorApplicationTest {

	@Autowired
	private CFAccessor cfAccessor;
	
	@Test
	void contextLoads() {
		assertThat(cfAccessor).isNotNull(); // Trivial test to ensure that the Unit test has at least some assertion
	}

	@AfterAll
	static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
}
