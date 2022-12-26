package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix="promregator")
public class PromregatorConfiguration {
	private List<Target> targets = new ArrayList<>();

	private AuthenticatorConfiguration authenticatorConfiguration;
	
	private List<TargetAuthenticatorConfiguration> targetAuthenticators = new ArrayList<>();
	
	public List<Target> getTargets() {
		return targets;
	}

	public void setTargets(List<Target> targets) {
		this.targets = targets;
	}

	public AuthenticatorConfiguration getAuthenticator() {
		return authenticatorConfiguration;
	}

	public void setAuthenticator(AuthenticatorConfiguration authenticatorConfiguration) {
		this.authenticatorConfiguration = authenticatorConfiguration;
	}

	public List<TargetAuthenticatorConfiguration> getTargetAuthenticators() {
		return targetAuthenticators;
	}

	public void setTargetAuthenticators(List<TargetAuthenticatorConfiguration> targetAuthenticators) {
		this.targetAuthenticators = targetAuthenticators;
	}
	
}
