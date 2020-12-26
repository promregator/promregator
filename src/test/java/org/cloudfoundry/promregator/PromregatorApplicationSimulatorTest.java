package org.cloudfoundry.promregator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.annotation.Nullable;

import org.cloudfoundry.promregator.cfaccessor.CFAccessorSimulator;
import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.scanner.Instance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { PromregatorApplication.class })
// Note that adding @ComponentScan(excludeFilters ... ) here does not make sense
// as PromregatorApplication will anyhow override it!

// Hint: activate the simulation profile, which is defined in src/test/resources/application.yml
@ActiveProfiles(profiles = {"simulation"})

@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
class PromregatorApplicationSimulatorTest {

	@Test
	void contextLoads() {
		assertThat(this.cfDiscoverer).isNotNull(); // Trivial test
	}
	
	@Autowired
	private CFDiscoverer cfDiscoverer;
	
	@Test
	void testDiscoveryWorks() {
		@Nullable
		List<Instance> actual = this.cfDiscoverer.discover(null, null);
		assertThat(200).isEqualTo(actual.size());
	}
	
	@Test
	void testSingleInstance() {
		@Nullable
		List<Instance> actual = this.cfDiscoverer.discover(appId -> appId.equals(CFAccessorSimulator.APP_UUID_PREFIX+"100"), 
				instance -> (CFAccessorSimulator.APP_UUID_PREFIX+"100:1").equals(instance.getInstanceId()));
		assertThat(actual).hasSize(1);
	}

	@AfterAll
	static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
}
