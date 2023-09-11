package org.cloudfoundry.promregator.auth;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.cloudfoundry.promregator.config.AbstractOAuth2XSUAAAuthenticationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloud.security.client.HttpClientFactory;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.xsuaa.client.DefaultOAuth2TokenService;
import com.sap.cloud.security.xsuaa.client.OAuth2TokenResponse;
import com.sap.cloud.security.xsuaa.tokenflows.ClientCredentialsTokenFlow;
import com.sap.cloud.security.xsuaa.tokenflows.TokenFlowException;
import com.sap.cloud.security.xsuaa.tokenflows.XsuaaTokenFlows;

public class OAuth2XSUAAEnricher implements AuthenticationEnricher, Closeable {

	private static final Logger log = LoggerFactory.getLogger(OAuth2XSUAAEnricher.class);

	private final CloseableHttpClient httpClient;
	private final ClientCredentialsTokenFlow tokenClient;

	OAuth2XSUAAEnricher(AbstractOAuth2XSUAAAuthenticationConfiguration config) {
		this(config, null);
	}

	/**
	 * @param config The configuration.
	 * @param tokenClient A token client from outside. This is intended only for unit tests.
	 */
	OAuth2XSUAAEnricher(AbstractOAuth2XSUAAAuthenticationConfiguration config, ClientCredentialsTokenFlow tokenClient) {
		super();
		OAuth2ServiceConfiguration c = new OAuth2ServiceConfig(config);

		if (tokenClient != null) {
			this.httpClient = null;
			this.tokenClient = tokenClient;
		} else {
			this.httpClient = HttpClientFactory.create(c.getClientIdentity());
			this.tokenClient = new XsuaaTokenFlows(new DefaultOAuth2TokenService(this.httpClient),
					new PromregatorOAuth2ServiceEndpointsProvider(c), c.getClientIdentity()).clientCredentialsTokenFlow();
		}
		
		final Set<String> scopesSet = config.getScopes();
		final String[] scopesArray = scopesSet.toArray(new String[0]);
		this.tokenClient.scopes(scopesArray);

		// Ensure getting the web token works (fail-fast)
		// We don't raise an exception, but we log the failure.
		try {
			String jwt = getJWT();
			if (jwt == null || jwt.isEmpty()) {
				log.error("Cannot obtain JWT for client '{}'.", c.getClientId());
			} else {
				log.debug("JWT obtained for client '{}': '{}******'", c.getClientId(), jwt.substring(0, Math.min(10, jwt.length()/3)));
			}
		} catch (TokenFlowException | RuntimeException e) {
			log.error("Cannot obtain JWT.", e);
		}
	}

	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		final String jwt = getJWTAndCatchExceptions();
		if (StringUtils.isAllBlank(jwt)) {
			log.error("Unable to enrich request with JWT");
			return;
		}
		httpget.setHeader("Authorization", String.format("Bearer %s", jwt));
	}

	private final String getJWTAndCatchExceptions() {
		String jwt = null;
		try {
			jwt = getJWT();
		} catch (TokenFlowException | RuntimeException e) {
			log.error("Unable to enrich request with JWT", e);
		}
		return jwt;
	}

	private final String getJWT() throws TokenFlowException {
		OAuth2TokenResponse tokenResponse = this.tokenClient.execute();
		if(tokenResponse == null) {
			return null;
		}
		return tokenResponse.getAccessToken();
	}

	// TODO: currently there is no good place for calling this method.
	// In case something goes wrong, the whole application will be
	// restarted. With that the resources used by the old application
	// we be released.
	@Override
	public void close() throws IOException {
		if (this.httpClient != null) {
			this.httpClient.close();
		}
	}
}
