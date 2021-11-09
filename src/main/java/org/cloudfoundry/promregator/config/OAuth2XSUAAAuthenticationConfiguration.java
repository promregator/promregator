package org.cloudfoundry.promregator.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class OAuth2XSUAAAuthenticationConfiguration {

	private String tokenServiceURL;

	private String tokenServiceCertURL;

	private String client_id;

	private String client_secret;

	private String client_certificates;

	private String client_key;

	private final Set<String> scopes = new HashSet<>();

	public String getTokenServiceURL() {
		return tokenServiceURL;
	}

	public void setTokenServiceURL(String url) {
		this.tokenServiceURL = url;
	}

	public String getTokenServiceCertURL() {
		return tokenServiceCertURL;
	}

	public void setTokenServiceCertURL(String url) {
		this.tokenServiceCertURL = url;
	}

	public String getClient_id() {
		return client_id;
	}

	public void setClient_id(String client_id) {
		this.client_id = client_id;
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

	public Set<String> getScopes() {
		return new HashSet<>(this.scopes);
	}

	public void setScopes(String scopes) {
		setScopes(new HashSet<>(Arrays.asList(scopes.split("[\\s,]+"))));
	}

	public void setScopes(Set<String> scopes) {
		this.scopes.clear();
		this.scopes.addAll(scopes);
	}
}
