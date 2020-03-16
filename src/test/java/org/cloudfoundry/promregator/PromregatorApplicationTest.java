package org.cloudfoundry.promregator;

import static org.assertj.core.api.Assertions.assertThat;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = PromregatorApplication.class)
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations="default.properties")
public class PromregatorApplicationTest {

	@Autowired
	private CFAccessor cfAccessor;
	
	@Test
	public void contextLoads() {
		assertThat(cfAccessor).isNotNull(); // Trivial test to ensure that the Unit test has at least some assertion
	}

}
