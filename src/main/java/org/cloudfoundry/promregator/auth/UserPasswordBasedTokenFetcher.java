package org.cloudfoundry.promregator.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.cloudfoundry.promregator.auth.OAuth2XSUAAEnricher.TokenResponse;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserPasswordBasedTokenFetcher extends TokenFetcher {

	private static final Logger log = LoggerFactory.getLogger(UserPasswordBasedTokenFetcher.class);

	UserPasswordBasedTokenFetcher(OAuth2XSUAAAuthenticationConfiguration authConfig)
			throws GeneralSecurityException, IOException {
		super(authConfig);
	}

	@Override
	protected CloseableHttpClient prepareHttpCient(OAuth2XSUAAAuthenticationConfiguration authConfig)
			throws GeneralSecurityException, IOException {
		return HttpClients.createDefault();
	}

	public TokenResponse getJWT(RequestConfig config) throws IOException {

		if (this.authConfig.getClient_id().contains(":")) {
			log.error("Security: jwtClient_id contains colon");
			return null;
		}

		if (this.authConfig.getClient_secret().contains(":")) {
			log.error("Security: jwtClient_id contains colon");
			return null;
		}

		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("response_type", "token"));

		HttpPost httpPost = preparePostRequest(this.authConfig.getTokenServiceURL(), config, params);
		httpPost.setHeader("Authorization", String.format("Basic %s",
				encodeUserPassword(this.authConfig.getClient_id(), this.authConfig.getClient_secret())));

		return fetchAndParseToken(httpPost);
	}

	private static String encodeUserPassword(String user, String password) {
		return Base64.getEncoder()
				.encodeToString(String.format("%s:%s", user, password).getBytes(StandardCharsets.UTF_8));
	}

}