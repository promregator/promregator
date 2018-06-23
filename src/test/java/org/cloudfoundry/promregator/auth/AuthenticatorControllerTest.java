package org.cloudfoundry.promregator.auth;

import java.util.List;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = AuthenticatorControllerSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class AuthenticatorControllerTest {

	@Autowired
	private AuthenticatorController subject;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;
	
	@Test
	public void testDefaultConfigurationCheck() {
		Assert.assertNotNull(subject);
		
		AuthenticationEnricher auth0 = subject.getAuthenticationEnricherById("unittestAuth0");
		Assert.assertTrue(auth0 instanceof BasicAuthenticationEnricher);
		
		AuthenticationEnricher auth1 = subject.getAuthenticationEnricherById("unittestAuth1");
		Assert.assertTrue(auth1 instanceof OAuth2XSUAAEnricher);

		AuthenticationEnricher auth2 = subject.getAuthenticationEnricherById("somethingInvalid");
		Assert.assertNull(auth2);

		AuthenticationEnricher auth3 = subject.getAuthenticationEnricherById(null);
		Assert.assertNull(auth3);
		
		List<Target> targets = this.promregatorConfiguration.getTargets();
		Target target0 = targets.get(0);
		
		Assert.assertEquals("testapp", target0.getApplicationName()); // only as safety for this test (not really a test subject)
		Assert.assertEquals(auth0, subject.getAuthenticationEnricherByTarget(target0));
		
		Target target1 = targets.get(1);
		
		Assert.assertEquals("testapp2", target1.getApplicationName()); // only as safety for this test (not really a test subject)
		Assert.assertEquals(auth1, subject.getAuthenticationEnricherByTarget(target1));
		
		Assert.assertTrue(subject.getAuthenticationEnricherByTarget(new Target()) instanceof NullEnricher);
		Assert.assertTrue(subject.getAuthenticationEnricherByTarget(null)  instanceof NullEnricher);
	}

}
