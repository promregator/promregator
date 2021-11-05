package org.cloudfoundry.promregator.auth;

import static com.sap.cloud.security.config.cf.CFConstants.CERTIFICATE;
import static com.sap.cloud.security.config.cf.CFConstants.CLIENT_ID;
import static com.sap.cloud.security.config.cf.CFConstants.CLIENT_SECRET;
import static com.sap.cloud.security.config.cf.CFConstants.KEY;
import static com.sap.cloud.security.config.cf.CFConstants.URL;
import static com.sap.cloud.security.config.cf.CFConstants.XSUAA.CERT_URL;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;

import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.config.Service;

public class OAuth2ServiceConfig implements OAuth2ServiceConfiguration {

	private final Map<String, String> properties = new HashMap<>();

	public OAuth2ServiceConfig(OAuth2XSUAAAuthenticationConfiguration config) {
		properties.put(CERTIFICATE, config.getClient_certificates());
		properties.put(KEY, config.getClient_key());
		properties.put(CLIENT_ID, config.getClient_id());
		properties.put(CLIENT_SECRET, config.getClient_secret());
		properties.put(URL, config.getUrl());
		properties.put(CERT_URL, config.getCertUrl());
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
		return new HashMap<String, String>(properties);
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
