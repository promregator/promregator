package org.cloudfoundry.promregator.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.cloudfoundry.promregator.auth.OAuth2XSUAAEnricher.TokenResponse;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloud.security.mtls.SSLContextFactory;

public class CertificateBasedTokenFetcher extends TokenFetcher {

	private static final Logger log = LoggerFactory.getLogger(CertificateBasedTokenFetcher.class);

	public CertificateBasedTokenFetcher(OAuth2XSUAAAuthenticationConfiguration authConfig)
			throws GeneralSecurityException, IOException {
		super(authConfig);
	}

	@Override
	public final CloseableHttpClient prepareHttpCient(OAuth2XSUAAAuthenticationConfiguration authConfig)
			throws GeneralSecurityException, IOException {
		SSLContext sslContext = SSLContextFactory.getInstance().create(this.authConfig.getClient_certificates(),
				this.authConfig.getClient_key());
		return HttpClients.custom().setSSLContext(sslContext).build();
	}

	public TokenResponse getJWT(RequestConfig config) throws IOException {

		if (this.authConfig.getClient_id().contains(":")) {
			log.error("Security: jwtClient_id contains colon");
			return null;
		}

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("client_id", this.authConfig.getClient_id()));

		HttpPost httpPost = preparePostRequest(this.authConfig.getTokenServiceURL(), config, params);

		try (CloseableHttpResponse response = httpClient.execute(httpPost)) {

			if (response.getStatusLine().getStatusCode() != 200) {
				log.error(String.format(
						"Server did not respond with ok while fetching JWT from token server; status code provided: %d",
						response.getStatusLine().getStatusCode()));
				return null;
			}

			return parseToken(response.getEntity());
		} catch (ClientProtocolException e) {
			log.error("Unable to read from the token server", e);
			throw e;
		} catch (IOException e) {
			log.error("IO Exception while reading from the token server", e);
			throw e;
		}
	}
}
