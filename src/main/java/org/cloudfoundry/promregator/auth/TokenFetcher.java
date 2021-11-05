package org.cloudfoundry.promregator.auth;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class TokenFetcher {

	//
	// START only during development ...
	static class Key {
		public String certurl;
		public String url;
		public String clientid;
		public String certificate;
		public String key;
		public String clientsecret;
		@JsonProperty("credential-type")
		public String credentialType;
	}

	public final static void main(String[] args) throws Exception {

		final Key key;

		try (InputStream i = new FileInputStream(args[0])) {
			key = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(i,
					Key.class);
		}

		OAuth2XSUAAAuthenticationConfiguration c = new OAuth2XSUAAAuthenticationConfiguration();
		c.setCertUrl(key.certurl);
		c.setClient_certificates(key.certificate);
		c.setClient_id(key.clientid);
		c.setClient_key(key.key);
		c.setClient_secret(key.clientsecret);
		c.setUrl(key.url);

		OAuth2XSUAAEnricher enricher = new OAuth2XSUAAEnricher(c);

		HttpGet get = new HttpGet();

		System.err.println("Before: " + get.getAllHeaders().length);
		Arrays.asList(get.getAllHeaders()).forEach(h -> System.err.println(h));

		enricher.enrichWithAuthentication(get);

		System.err.println("After: " + get.getAllHeaders().length);
		Arrays.asList(get.getAllHeaders()).forEach(h -> System.err.println(h));

	}
	// END only during development
	//
}
