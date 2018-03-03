package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="promregator")
@EnableConfigurationProperties
public class PromregatorConfiguration {
	private List<Target> targets = new ArrayList<>();

	private AuthenticationConfiguration authenticationConfiguration;
	
	public List<Target> getTargets() {
		return targets;
	}

	public void setTargets(List<Target> targets) {
		this.targets = targets;
	}

	public AuthenticationConfiguration getAuthenticator() {
		return authenticationConfiguration;
	}

	public void setAuthenticator(AuthenticationConfiguration authenticationConfiguration) {
		this.authenticationConfiguration = authenticationConfiguration;
	}
	
}
