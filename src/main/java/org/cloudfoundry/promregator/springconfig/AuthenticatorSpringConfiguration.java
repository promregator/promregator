package org.cloudfoundry.promregator.springconfig;

import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticatorSpringConfiguration {
	
	@Bean
	public AuthenticatorController authenticatorController() {
		return new AuthenticatorController();
	}
}
