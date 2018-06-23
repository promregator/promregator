package org.cloudfoundry.promregator.config;

public class TargetAuthenticatorConfiguration extends AuthenticatorConfiguration {
	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
