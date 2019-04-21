package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="promregator")
@EnableConfigurationProperties
public class PromregatorConfiguration {
	private List<ConfigurationTarget> targets = new ArrayList<>();

	private AuthenticatorConfiguration authenticatorConfiguration;
	
	private List<TargetAuthenticatorConfiguration> targetAuthenticators = new ArrayList<>();
	
	public List<Target> getTargets() {
		List<Target> result = new LinkedList<>();
		
		for (ConfigurationTarget ct : this.targets) {
			List<Target> unrolledTargets = ct.unrollTargets();
			result.addAll(unrolledTargets);
		}
		
		return result;
	}

	public void setTargets(List<ConfigurationTarget> targets) {
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
