package org.cloudfoundry.promregator.springconfig;

import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticatorSpringConfiguration {
	
	@Bean
	public AuthenticatorController authenticatorController(PromregatorConfiguration promregatorConfiguration) {
		return new AuthenticatorController(promregatorConfiguration);
	}
}
