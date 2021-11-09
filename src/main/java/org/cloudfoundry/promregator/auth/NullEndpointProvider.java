package org.cloudfoundry.promregator.auth;

import static com.sap.cloud.security.xsuaa.Assertions.assertNotNull;

import java.net.URI;

import com.sap.cloud.security.config.CredentialType;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceEndpointsProvider;

public class NullEndpointProvider implements OAuth2ServiceEndpointsProvider {

	private final URI baseUri;
	private final URI certUri;

	public NullEndpointProvider(OAuth2ServiceConfiguration config) {
		assertNotNull(config, "OAuth2ServiceConfiguration must not be null.");
		this.baseUri = config.getUrl();
		if(config.getCredentialType() == CredentialType.X509) {
			this.certUri = config.getCertUrl();
		} else {
			this.certUri = null;
		}
	}

	@Override
	public URI getTokenEndpoint() {
		return certUri != null ? certUri : baseUri;
	}

	@Override
	public URI getAuthorizeEndpoint() {
		throw new UnsupportedOperationException();
	}

	@Override
	public URI getJwksUri() {
		throw new UnsupportedOperationException();
	}
}
