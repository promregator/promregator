package org.cloudfoundry.promregator.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuth2XSUAAAuthenticationConfiguration {

	public final static String deprecatedTokenServiceURLProperty = "tokenServiceURL";
	public final static String useInsteadXsuaaServiceURLProperty = "xsuaaServiceURL";

	private final static String DEEFAULT_OAUTH_TOKEN_URL_PATH = "/oauth/token";

	private static final Logger log = LoggerFactory.getLogger(OAuth2XSUAAAuthenticationConfiguration.class);

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

		log.warn("Deprecated property '{}' found. Use '{}' instead without providing trailing '{}'.'", deprecatedTokenServiceURLProperty, useInsteadXsuaaServiceURLProperty, DEEFAULT_OAUTH_TOKEN_URL_PATH);
		if (getXsuaaServiceURL() != null) {
			// this does not work always. In case tokenServiceURL is handled first the xsuaaServiceURL is null at this point in time.
			log.warn("Ignoring deprecated property '{}' ({}) since '{}' ({}) has been provided", deprecatedTokenServiceURLProperty, url, useInsteadXsuaaServiceURLProperty, getXsuaaServiceURL());
			return;
		}
		try {
			URI u = new URI(url);
			String rewritten = new URIBuilder().setScheme(u.getScheme()).setHost(u.getHost()).setPort(u.getPort())
					.setPath(u.getPath().replaceAll(DEEFAULT_OAUTH_TOKEN_URL_PATH + "$", "")).build().toASCIIString();
			log.warn("Rewriting '{}' ({}) to '{}' in order to use it as '{}'.", deprecatedTokenServiceURLProperty, url, rewritten, useInsteadXsuaaServiceURLProperty);
			setXsuaaServiceURL(rewritten);
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
