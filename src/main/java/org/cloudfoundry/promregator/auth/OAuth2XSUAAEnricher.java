package org.cloudfoundry.promregator.auth;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.NettyConnectionManager;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;

import com.google.gson.Gson;

import io.netty.handler.codec.json.JsonObjectDecoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.ByteBufFlux;
import reactor.ipc.netty.http.client.HttpClient;

public class OAuth2XSUAAEnricher implements AuthenticationEnricher {
	
	private static final Logger log = Logger.getLogger(OAuth2XSUAAEnricher.class);
	
	final private OAuth2XSUAAAuthenticationConfiguration config;

	private HttpClient httpClient;
	
	public OAuth2XSUAAEnricher(OAuth2XSUAAAuthenticationConfiguration config, String proxyHost, int proxyPort) {
		super();
		this.config = config;
		
		httpClient = NettyConnectionManager.determineNettyClient(proxyHost, proxyPort);
	}

	@Override
	public void enrichWithAuthentication(HTTPRequestFacade facade) {
		String jwt = getBufferedJWT();
		if (jwt == null) {
			log.error("Unable to enrich request with JWT");
			return;
		}
		
		facade.addHeader("Authorization", String.format("Bearer %s", jwt));
	}

	private String bufferedJwt = null;
	private Instant validUntil = null;
	
	private synchronized String getBufferedJWT() {
		if (this.bufferedJwt == null) {
			// no JWT available
			this.bufferedJwt = getJWT();
		} else if (Instant.now().isAfter(this.validUntil)) {
			// JWT is expired
			this.bufferedJwt = getJWT();
		}
		
		return bufferedJwt;
	}

	private String getJWT() {
		log.info("Fetching new JWT token");
		
		String url = String.format("%s?grant_type=client_credentials", this.config.getTokenServiceURL());
		if (this.config.getScopes() != null) {
			// see also https://www.oauth.com/oauth2-servers/access-tokens/client-credentials/
			try {
				url += String.format("&scope=%s", URLEncoder.encode(this.config.getScopes(), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				log.error("Error while adding scope information to request URL", e);
				return null;
			}
		}
		
		String base64Auth = this.determineBase64Authentication();
		
		String responseString = this.httpClient.post(url, request -> {
			request = request.chunkedTransfer(false)
			.addHeader("Authorization", String.format("Basic %s", base64Auth))
			.addHeader("Content-Type", "application/x-www-form-urlencoded")

			/* closing the connection afterwards is important!
			 * Background: httpclient will otherwise try to keep the connection open.
			 * We won't be calling often anyway, so the server would be drained from resources.
			 * Moreover, if the server has gone away in the meantime, the next attempt to 
			 * call would fail with a recv error when reading from the socket.
			 */
			.addHeader("Connection", "close")
			.keepAlive(false);
			
			return request.sendString(Mono.just("response_type=token"));
		}).flatMap(resp -> {
			if (resp.status().code() != 200) {
				log.error(String.format("Server did not respond with ok while fetching JWT from token server; status code provided: %d", resp.status().code()));
				return Mono.empty();
			}
			
			return resp.receive().aggregate().asString();
		})
		.block(Duration.ofMillis(5000)); // TODO make this timeout configurable
		
		Gson gson = new Gson();
		TokenResponse oAuthResponse = gson.fromJson(responseString, TokenResponse.class);
		
		String jwt = oAuthResponse.getAccessToken();
		log.info(String.format("JWT token retrieved: %s...", jwt.substring(0, Math.min(jwt.length() / 2, 30))));

		int timeOutForUs = Math.max(oAuthResponse.getExpiresIn() - 30, oAuthResponse.getExpiresIn() / 2);
		this.validUntil = Instant.now().plus(timeOutForUs, ChronoUnit.SECONDS);
		
		log.info(String.format("JWT is valid until %s", this.validUntil.toString()));
		
		return jwt;
	}

	private String determineBase64Authentication() {
		if (this.config.getClient_id().contains(":")) {
			throw new Error("Security: jwtClient_id contains colon");
		}

		if (this.config.getClient_secret().contains(":")) {
			throw new Error("Security: jwtClient_id contains colon");
		}
		
		String b64encoding = String.format("%s:%s", this.config.getClient_id(), this.config.getClient_secret());
		
		byte[] encodedBytes = null;
		try {
			encodedBytes = b64encoding.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.error("Unable to b64-encode using UTF-8", e);
			return null;
		}
		return Base64.getEncoder().encodeToString(encodedBytes);
	}
	
	private static class TokenResponse {
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
