package org.cloudfoundry.promregator.auth;

import static com.sap.cloud.security.config.ServiceConstants.CERTIFICATE;
import static com.sap.cloud.security.config.ServiceConstants.CLIENT_ID;
import static com.sap.cloud.security.config.ServiceConstants.CLIENT_SECRET;
import static com.sap.cloud.security.config.ServiceConstants.KEY;
import static com.sap.cloud.security.config.ServiceConstants.URL;
import static com.sap.cloud.security.config.ServiceConstants.XSUAA.CERT_URL;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.cloudfoundry.promregator.config.AbstractOAuth2XSUAAAuthenticationConfiguration;
import org.cloudfoundry.promregator.config.OAuth2XSUAABasicAuthenticationConfiguration;
import org.cloudfoundry.promregator.config.OAuth2XSUAACertificateAuthenticationConfiguration;

import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.config.Service;

public class OAuth2ServiceConfig implements OAuth2ServiceConfiguration {

	private final Map<String, String> properties = new HashMap<>();

	public OAuth2ServiceConfig(AbstractOAuth2XSUAAAuthenticationConfiguration config) {
		properties.put(CLIENT_ID, config.getClient_id());
		if (config instanceof OAuth2XSUAABasicAuthenticationConfiguration c) {
			properties.put(CLIENT_SECRET, c.getClient_secret());
			properties.put(URL, c.getTokenServiceURL());
		} else if (config instanceof OAuth2XSUAACertificateAuthenticationConfiguration c) {
			properties.put(CERTIFICATE, c.getClient_certificates());
			properties.put(KEY, c.getClient_key());
			properties.put(CERT_URL, c.getTokenServiceCertURL());
		} else {
			throw new IllegalArgumentException(String.format("Invalid authentication configuration type '%s'", config.getClass().getName()));
		}
	}

	@Override
	public String getClientId() {
		return properties.get(CLIENT_ID);
	}

	@Override
	public String getClientSecret() {
		return properties.get(CLIENT_SECRET);
	}

	@Override
	public URI getUrl() {
		return toUri(properties.get(URL));
	}

	@Override
	public URI getCertUrl() {
		return toUri(properties.get(CERT_URL));
	}

	@Override
	public String getProperty(String name) {
		return properties.get(name);
	}

	@Override
	public Map<String, String> getProperties() {
		return new HashMap<>(properties);
	}

	@Override
	public boolean hasProperty(String name) {
		return properties.containsKey(name);
	}

	@Override
	public Service getService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isLegacyMode() {
		return false;
	}

	private static URI toUri(String uri) {
		if (uri == null) {
			return null;
		}
		try {
			return new URI(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
}
