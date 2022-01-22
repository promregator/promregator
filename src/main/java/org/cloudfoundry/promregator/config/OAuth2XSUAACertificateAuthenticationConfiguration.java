package org.cloudfoundry.promregator.config;

public class OAuth2XSUAACertificateAuthenticationConfiguration extends AbstractOAuth2XSUAAAuthenticationConfiguration {

	private String tokenServiceCertURL;

	private String client_certificates;

	private String client_key;

	public String getTokenServiceCertURL() {
		return tokenServiceCertURL;
	}

	public void setTokenServiceCertURL(String url) {
		this.tokenServiceCertURL = url;
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
}
