package org.cloudfoundry.promregator.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHeaders;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.sap.cloud.security.client.HttpClientException;
import com.sap.cloud.security.config.ClientIdentity;
import com.sap.cloud.security.mtls.SSLContextFactory;

public class PromregatorHttpClientFactory implements com.sap.cloud.security.client.HttpClientFactory {

	/*
	 * This factory impl is taken from here:
	 * https://github.com/SAP/cloud-security-xsuaa-integration/blob/
	 * 9e65f91a926e782403df996d99d0eff43de63d24/token-client/src/main/java/com/sap/
	 * cloud/security/client/DefaultHttpClientFactory.java
	 */

	private static final Logger log = LoggerFactory.getLogger(PromregatorHttpClientFactory.class);

	@Override
	public CloseableHttpClient createClient(ClientIdentity clientIdentity) throws HttpClientException {

		log.info("Using '{}' http client factory.", getClass().getSimpleName());
		
		HttpClientBuilder httpClientBuilder = HttpClients.custom();
		/*
		 * For why this is necessary and should not be harmful in practice, refer
		 * to https://github.com/promregator/promregator/pull/206#discussion_r752164423
		 */
		httpClientBuilder.setDefaultHeaders(Lists.newArrayList(new BasicHeader(HttpHeaders.CONNECTION, "close")));
		
		if (clientIdentity != null && clientIdentity.isCertificateBased()) {
			log.debug("Setting up HTTPS client with: certificate: {}\n", clientIdentity.getCertificate());
			SSLContext sslContext;
			try {
				sslContext = SSLContextFactory.getInstance().create(clientIdentity);
			} catch (IOException | GeneralSecurityException e) {
				throw new HttpClientException(
						"Couldn't set up https client for service provider. %s.".formatted(e.getMessage()));
			}
			SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
			httpClientBuilder.setSSLContext(sslContext).setSSLSocketFactory(socketFactory);
		} else {
			log.debug("Setting up http client without ssl context");
		}
		return httpClientBuilder.build();
	}
}
