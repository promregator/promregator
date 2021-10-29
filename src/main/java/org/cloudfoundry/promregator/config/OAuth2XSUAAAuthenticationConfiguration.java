package org.cloudfoundry.promregator.config;

public class OAuth2XSUAAAuthenticationConfiguration {
	private String tokenServiceURL;

	private String client_id;

	private String client_secret;

	private String client_certificates;

	private String client_key;

	private String scopes;

	public String getTokenServiceURL() {
		return tokenServiceURL;
	}

	public void setTokenServiceURL(String tokenServiceURL) {
		this.tokenServiceURL = tokenServiceURL;
	}

	public String getClient_id() {
		return client_id;
	}

	public void setClient_id(String clent_id) {
		this.client_id = clent_id;
	}

	public String getClient_secret() {
		return client_secret;
	}

	public void setClient_certificates(String client_certificates) {
		this.client_certificates = client_certificates;
	}

	public String getClient_certificates() {
		return this.client_certificates;
	}

	public void setClient_key(String client_key) {
		this.client_key = client_key;
	}

	public String getClient_key() {
		return this.client_key;
	}

	public void setClient_secret(String client_secret) {
		this.client_secret = client_secret;
	}

	public String getScopes() {
		return scopes;
	}

	public void setScopes(String scopes) {
		this.scopes = scopes;
	}

}
