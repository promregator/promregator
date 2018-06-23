package org.cloudfoundry.promregator.auth;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.springconfig.AuthenticatorSpringConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({AuthenticatorSpringConfiguration.class})
public class AuthenticatorControllerSpringApplication {

	@Bean
	public PromregatorConfiguration promregatorConfiguration() {
		return new PromregatorConfiguration();
	}
	
}
