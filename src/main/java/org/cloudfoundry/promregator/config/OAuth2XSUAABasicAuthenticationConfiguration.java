package org.cloudfoundry.promregator.config;

public class OAuth2XSUAABasicAuthenticationConfiguration extends AbstractOAuth2XSUAAAuthenticationConfiguration {

	private String tokenServiceURL;

	private String client_secret;

	public String getTokenServiceURL() {
		return tokenServiceURL;
	}

	public void setTokenServiceURL(String url) {
		this.tokenServiceURL = url;
	}

	public String getClient_secret() {
		return client_secret;
	}

	public void setClient_secret(String client_secret) {
		this.client_secret = client_secret;
	}
}
