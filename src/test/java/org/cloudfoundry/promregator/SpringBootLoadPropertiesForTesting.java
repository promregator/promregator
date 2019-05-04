package org.cloudfoundry.promregator;


import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@SpringBootTest(classes = { SpringBootLoadPropertiesForTestingSpringApplication.class })
@RunWith(SpringRunner.class)
@ContextConfiguration(initializers = { ConfigFileApplicationContextInitializer.class })
@ActiveProfiles(profiles= {"springBootLoadPropertiesForTesting"})
public class SpringBootLoadPropertiesForTesting {

	@ClassRule
	public final static EnvironmentVariables environmentVariables = new EnvironmentVariables().set("ENCRYPT_KEY", "mySecretKey");


	@Autowired
	private SpringBootLoadPropertiesForTestingSpringApplication springBootLoadPropertiesForTestingSpringApplication;

	@Test
	public void testContextLoads() {
		springBootLoadPropertiesForTestingSpringApplication.check();
		Assert.assertTrue(true);
	}

	@Test
	public void testContextLoadsWithEncryptedValue() {
		String secretValue = springBootLoadPropertiesForTestingSpringApplication.getSecretValue();
		assertThat("passwords do not match but should", secretValue, is("mysecret"));
	}

	@Test
	public void testContextLoadsWithEncryptedValueNotMatching() {
		String secretValue = springBootLoadPropertiesForTestingSpringApplication.getSecretValue();
		assertThat("passwords do match but shouldn't", secretValue, not("notMySecret"));
	}
}
