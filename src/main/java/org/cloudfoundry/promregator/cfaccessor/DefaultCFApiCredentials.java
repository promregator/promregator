package org.cloudfoundry.promregator.cfaccessor;

public class DefaultCFApiCredentials implements CFApiCredentials {
	private final String username;
	private final String password;

	public DefaultCFApiCredentials(final String username, final String password) {
		this.username = username;
		this.password = password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public String getPassword() {
		return password;
	}
}
