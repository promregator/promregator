package org.cloudfoundry.promregator;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest(classes = { SpringBootLoadPropertiesForTestingSpringApplication.class })
@ContextConfiguration(initializers = { ConfigDataApplicationContextInitializer.class })
@ActiveProfiles(profiles= {"springBootLoadPropertiesForTesting", "springBootLoadPropertiesForEncryptionTesting"})
@TestPropertySource(properties = "encrypt.key=mySecretKey")
public class SpringBootLoadPropertiesForTesting {

	@Autowired
	private SpringBootLoadPropertiesForTestingSpringApplication springBootLoadPropertiesForTestingSpringApplication;

	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testContextLoads() {
		springBootLoadPropertiesForTestingSpringApplication.check();
		Assertions.assertTrue(true);
	}

	@Test
	void testContextLoadsWithEncryptedValue() {
		String secretValue = springBootLoadPropertiesForTestingSpringApplication.getSecretValue();
		Assertions.assertEquals("mysecret", secretValue);
	}
}
