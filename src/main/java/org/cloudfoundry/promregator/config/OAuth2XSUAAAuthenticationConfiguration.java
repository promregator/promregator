package org.cloudfoundry.promregator.config;

public class OAuth2XSUAAAuthenticationConfiguration {
	private String tokenServiceURL;
	
	private String client_id;
	
	private String client_secret;

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
