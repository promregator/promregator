package org.cloudfoundry.promregator.auth;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.cloudfoundry.promregator.auth.OAuth2XSUAAEnricher.TokenResponse;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.json.JsonSanitizer;

public abstract class TokenFetcher {

	private static final Logger log = LoggerFactory.getLogger(TokenFetcher.class);

	protected final OAuth2XSUAAAuthenticationConfiguration authConfig;

	protected final CloseableHttpClient httpClient;

	protected TokenFetcher(OAuth2XSUAAAuthenticationConfiguration authConfig)
			throws GeneralSecurityException, IOException {
		this.authConfig = authConfig;
		this.httpClient = prepareHttpCient(authConfig);
	}

	public abstract TokenResponse getJWT(RequestConfig config) throws IOException;

	protected abstract CloseableHttpClient prepareHttpCient(OAuth2XSUAAAuthenticationConfiguration authConfig)
			throws GeneralSecurityException, IOException;

	protected HttpPost preparePostRequest(String tokenServiceUrl, RequestConfig config,
			List<NameValuePair> additionalParams) {
		HttpPost httpPost = new HttpPost(this.authConfig.getTokenServiceURL());
		httpPost.setConfig(config);

		/*
		 * closing the connection afterwards is important! Background: httpclient will
		 * otherwise try to keep the connection open. We won't be calling often anyway,
		 * so the server would be drained from resources. Moreover, if the server has
		 * gone away in the meantime, the next attempt to call would fail with a recv
		 * error when reading from the socket.
		 */
		httpPost.setHeader("Connection", "close");

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.addAll(additionalParams);
		params.add(new BasicNameValuePair("grant_type", "client_credentials"));
		if (this.authConfig.getScopes() != null) {
			params.add(new BasicNameValuePair("scope", this.authConfig.getScopes()));
		}
		UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(params, Consts.UTF_8);
		httpPost.setEntity(formEntity);

		return httpPost;
	}

	protected TokenResponse parseToken(String responseBody) {
		try {
			return new Gson().fromJson(JsonSanitizer.sanitize(responseBody), TokenResponse.class);
		} catch (ParseException e) {
			log.error("GSON parser exception on JWT response from token server", e);
			throw e;
		}
	}

	public void close() throws IOException {
		httpClient.close();
	}
}
