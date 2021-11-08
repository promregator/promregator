package org.cloudfoundry.promregator.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.lite.config.CfTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = AuthenticatorControllerSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class AuthenticatorControllerTest {

	@Autowired
	private AuthenticatorController subject;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;
	
	@Test
	void testDefaultConfigurationCheck() {
		assertThat(subject).isNotNull();
		
		AuthenticationEnricher auth0 = subject.getAuthenticationEnricherById("unittestAuth0");
		assertThat(auth0).isInstanceOf(BasicAuthenticationEnricher.class);
		
		AuthenticationEnricher auth1 = subject.getAuthenticationEnricherById("unittestAuth1");
		assertThat(auth1).isInstanceOf(OAuth2XSUAAEnricher.class);

		AuthenticationEnricher auth2 = subject.getAuthenticationEnricherById("somethingInvalid");
		assertThat(auth2).isNull();

		AuthenticationEnricher auth3 = subject.getAuthenticationEnricherById(null);
		assertThat(auth3).isNull();
		
		List<CfTarget> targets = this.promregatorConfiguration.getTargets();
		CfTarget target0 = targets.get(0);
		
		Assertions.assertEquals("testapp",target0.getApplicationName()); // only as safety for this test (not really a test subject)
		Assertions.assertEquals(auth0, subject.getAuthenticationEnricherByTarget(target0));
		
		CfTarget target1 = targets.get(1);
		
		Assertions.assertEquals("testapp2", target1.getApplicationName()); // only as safety for this test (not really a test subject)
		Assertions.assertEquals(auth1, subject.getAuthenticationEnricherByTarget(target1));
		
		assertThat(subject.getAuthenticationEnricherByTarget(new CfTarget())).isInstanceOf(NullEnricher.class);
		assertThat(subject.getAuthenticationEnricherByTarget(null)).isInstanceOf(NullEnricher.class);
	}

}
