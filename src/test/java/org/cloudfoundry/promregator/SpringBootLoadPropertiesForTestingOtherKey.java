package org.cloudfoundry.promregator;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;


@SpringBootTest(classes = { SpringBootLoadPropertiesForTestingSpringApplication.class })
@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = { ConfigFileApplicationContextInitializer.class })
@ActiveProfiles(profiles= {"springBootLoadPropertiesForTestingOtherKey", "springBootLoadPropertiesForEncryptionTesting"})
@TestPropertySource(properties = "encrypt.key=someotherkey")
class SpringBootLoadPropertiesForTestingOtherKey {

	@Autowired
	private SpringBootLoadPropertiesForTestingSpringApplication springBootLoadPropertiesForTestingSpringApplication;

	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testContextLoads() {
		springBootLoadPropertiesForTestingSpringApplication.check();
		assertThat(true).as("context loads").isTrue();
	}

	@Test
	void testContextLoadsWithEncryptedValue() {
		String secretValue = springBootLoadPropertiesForTestingSpringApplication.getSecretValue();
		assertThat(secretValue).as("passwords do not match but should").isEqualTo("myothersecret");
	}
}
