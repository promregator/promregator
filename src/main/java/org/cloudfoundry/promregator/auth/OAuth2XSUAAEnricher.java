package org.cloudfoundry.promregator.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuth2XSUAAEnricher implements AuthenticationEnricher {
	
	private static final Logger log = LoggerFactory.getLogger(OAuth2XSUAAEnricher.class);
	
	static final CloseableHttpClient httpclient = HttpClients.createDefault();
	
	private final TokenFetcher tokenFetcher;

	public OAuth2XSUAAEnricher(OAuth2XSUAAAuthenticationConfiguration config) {
		super();
		this.tokenFetcher = setupTokenFetcher(config);
	}

	private final static TokenFetcher setupTokenFetcher(OAuth2XSUAAAuthenticationConfiguration config) {
		if (config.getClient_id() != null) {
			try {
				if (config.getClient_secret() != null) {
					return new UserPasswordBasedTokenFetcher(config);
				} else if (config.getClient_certificates() != null && config.getClient_key() != null) {
					return new CertificateBasedTokenFetcher(config);
				}
			} catch (GeneralSecurityException | IOException e) {
				log.error("Cannot instanciate token fetcher", e);
			}
		}
		return null;
	}

	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		final RequestConfig requestConfig = httpget.getConfig();
		
		final String jwt = getBufferedJWT(requestConfig);
		if (jwt == null) {
			log.error("Unable to enrich request with JWT");
			return;
		}
		
		httpget.setHeader("Authorization", String.format("Bearer %s", jwt));
	}

	private String bufferedJwt = null;
	private Instant validUntil = null;
	
	private synchronized String getBufferedJWT(RequestConfig config) {
		if (this.bufferedJwt == null || Instant.now().isAfter(this.validUntil)) {
			// JWT is not available or expired
			this.bufferedJwt = getJWT(config);
		}
		
		return bufferedJwt;
	}

	private String getJWT(RequestConfig config) {
		
		if (tokenFetcher == null) {
			log.error("Cannot retrieve token. No token fetcher available. Check auth config.");
			return null;
		}

		log.info("Fetching new JWT token");

		TokenResponse oAuthResponse = null;
		try {
			oAuthResponse = tokenFetcher.getJWT(config);
			if (oAuthResponse == null) {
				log.error("Cannot retrieve token");
				return null;
			}
		} catch (IOException e) {
			log.error("Cannot retrieve token", e);
			return null;
		}

		String jwt = oAuthResponse.getAccessToken();
		log.info(String.format("JWT token retrieved: %s...", jwt.substring(0, Math.min(jwt.length() / 2, 30))));

		int timeOutForUs = Math.max(oAuthResponse.getExpiresIn() - 30, oAuthResponse.getExpiresIn() / 2);
		this.validUntil = Instant.now().plus(timeOutForUs, ChronoUnit.SECONDS);

		log.info(String.format("JWT is valid until %s", this.validUntil.toString()));

		return jwt;
	}

	static class TokenResponse {
		private String access_token;
		private int expires_in;
		
		public String getAccessToken() {
			return access_token;
		}

		public int getExpiresIn() {
			return expires_in;
		}
	}
	
}
