package org.cloudfoundry.promregator.mockServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class DefaultOAuthHttpHandler implements HttpHandler {
	private int counterCalled = 0;
	
	private String response = "";

	
	private static final Logger log = LoggerFactory.getLogger(DefaultOAuthHttpHandler.class);
	
	@Override
	public void handle(HttpExchange he) throws IOException {
		log.debug("Request was received at handler");
		this.counterCalled++;

		URI requestedUri = he.getRequestURI();

		Assertions.assertEquals("/oauth/token", requestedUri.getPath());
		Assertions.assertEquals("grant_type=client_credentials", requestedUri.getRawQuery());
		Assertions.assertEquals("POST", he.getRequestMethod());

		// check the body
		InputStreamReader isr = new InputStreamReader(he.getRequestBody(), StandardCharsets.UTF_8);
		BufferedReader br = new BufferedReader(isr);
		String query = br.readLine();

		Assertions.assertEquals("response_type=token", query);
		
		// check the credentials
		String authValue = he.getRequestHeaders().getFirst("Authorization");
		Assertions.assertEquals("Basic Y2xpZW50X2lkOmNsaWVudF9zZWNyZXQ=", authValue);

		String contentTypeValue = he.getRequestHeaders().getFirst("Content-Type");
		Assertions.assertEquals("application/json", contentTypeValue);
		
		// send response
		he.sendResponseHeaders(200, this.response.length());

		OutputStream os = he.getResponseBody();
		os.write(response.getBytes());
		os.flush();
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public int getCounterCalled() {
		return counterCalled;
	}
}
