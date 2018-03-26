package org.cloudfoundry.promregator.springconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
// see also https://dzone.com/articles/spring-security-basic-authentication-example-1
public class BasicAuthenticationSpringConfiguration {

	@Bean
	public BasicAuthenticationFilter basicAuthFilter(AuthenticationManager authenticationManager,
			BasicAuthenticationEntryPoint basicAuthEntryPoint) {
		return new BasicAuthenticationFilter(authenticationManager, basicAuthEntryPoint());
	}

	@Bean
	public BasicAuthenticationEntryPoint basicAuthEntryPoint() {
		BasicAuthenticationEntryPoint bauth = new BasicAuthenticationEntryPoint();
		bauth.setRealmName("Promregator");
		return bauth;
	}
}
