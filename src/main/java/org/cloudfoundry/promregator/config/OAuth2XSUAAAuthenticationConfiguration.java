package org.cloudfoundry.promregator.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.client.utils.URIBuilder;

public class OAuth2XSUAAAuthenticationConfiguration {
	private String xsuaaServiceURL;

	private String xsuaaServiceCertURL;

	private String client_id;

	private String client_secret;

	private String client_certificates;

	private String client_key;

	private final Set<String> scopes = new HashSet<>();

	public String getXsuaaServiceURL() {
		return xsuaaServiceURL;
	}

	public void setTokenServiceURL(String url) {
		try {
			URI u = new URI(url);
			setXsuaaServiceURL(new URIBuilder().setScheme(u.getScheme()).setHost(u.getHost()).setPort(u.getPort())
					.setPath(u.getPath().replaceAll("/oauth/token$", "")).build().toASCIIString());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public void setXsuaaServiceURL(String xsuaaServiceURL) {
		this.xsuaaServiceURL = xsuaaServiceURL;
	}

	public String getXsuaaServiceCertURL() {
		return xsuaaServiceCertURL;
	}

	public void setXsuaaServiceCertURL(String xsuaaServiceCertURL) {
		this.xsuaaServiceCertURL = xsuaaServiceCertURL;
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
