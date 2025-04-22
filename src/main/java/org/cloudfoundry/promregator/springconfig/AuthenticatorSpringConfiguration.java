package org.cloudfoundry.promregator.springconfig;

import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.cloudfoundry.promregator.cfaccessor.CFApiCredentials;
import org.cloudfoundry.promregator.cfaccessor.DefaultCFApiCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticatorSpringConfiguration {
	private static final Logger log = LoggerFactory.getLogger(AuthenticatorSpringConfiguration.class);
	
	@Bean
	public AuthenticatorController authenticatorController() {
		return new AuthenticatorController();
	}

	@Bean
	@ConditionalOnProperty(prefix="cf",name="username")
	public CFApiCredentials defaultCFApiCredentials(@Value("${cf.username}") String username, @Value("${cf.password}") String password) {
		log.debug("Found cf.username in configuration, using DefaultCFApiCredentials");
		return new DefaultCFApiCredentials(username, password);
	}
}
