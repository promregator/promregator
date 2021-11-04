package org.cloudfoundry.promregator.auth;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloud.security.client.HttpClientFactory;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.xsuaa.client.DefaultOAuth2TokenService;
import com.sap.cloud.security.xsuaa.client.XsuaaDefaultEndpoints;
import com.sap.cloud.security.xsuaa.tokenflows.ClientCredentialsTokenFlow;
import com.sap.cloud.security.xsuaa.tokenflows.TokenFlowException;
import com.sap.cloud.security.xsuaa.tokenflows.XsuaaTokenFlows;

public class OAuth2XSUAAEnricher implements AuthenticationEnricher {

	private static final Logger log = LoggerFactory.getLogger(OAuth2XSUAAEnricher.class);

	private final CloseableHttpClient httpClient;
	private final ClientCredentialsTokenFlow tokenClient;

	public OAuth2XSUAAEnricher(OAuth2XSUAAAuthenticationConfiguration config) {
		super();
		OAuth2ServiceConfiguration c = new OAuth2ServiceConfig(config);
		this.httpClient = HttpClientFactory.create(c.getClientIdentity());
		this.tokenClient = new XsuaaTokenFlows(new DefaultOAuth2TokenService(httpClient), new XsuaaDefaultEndpoints(c),
				c.getClientIdentity()).clientCredentialsTokenFlow();
		this.tokenClient.scopes(config.getScopes().toArray(new String[0]));
	}

	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		final String jwt = getJWT();
		if (jwt == null) {
			log.error("Unable to enrich request with JWT");
			return;
		}
		httpget.setHeader("Authorization", String.format("Bearer %s", jwt));
	}

	private String getJWT() {
		String jwt = null;
		try {
			jwt = tokenClient.execute().getAccessToken();
		} catch (TokenFlowException e) {
			log.error("Unable to enrich request with JWT", e);
		}
		return jwt;
	}

	public void close() throws IOException {
		this.httpClient.close();
	}
}
