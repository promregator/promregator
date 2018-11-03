package org.cloudfoundry.promregator;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = { SpringBootLoadPropertiesForTestingSpringApplication.class })
@RunWith(SpringRunner.class)
@ContextConfiguration(initializers = { ConfigFileApplicationContextInitializer.class })
@ActiveProfiles(profiles= {"springBootLoadPropertiesForTesting"})
public class SpringBootLoadPropertiesForTesting {

	@Autowired
	private SpringBootLoadPropertiesForTestingSpringApplication springBootLoadPropertiesForTestingSpringApplication;

	@Test
	public void testContextLoads() {
		springBootLoadPropertiesForTestingSpringApplication.check();
		Assert.assertTrue(true);
	}
}
