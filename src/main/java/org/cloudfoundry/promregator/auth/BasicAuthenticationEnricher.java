package org.cloudfoundry.promregator.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;
import org.springframework.web.reactive.function.client.WebClient;

public class BasicAuthenticationEnricher implements AuthenticationEnricher {
	
	private final BasicAuthenticationConfiguration authenticatorConfig;
	
	public BasicAuthenticationEnricher(BasicAuthenticationConfiguration authenticatorConfig) {
		super();
		this.authenticatorConfig = authenticatorConfig;
	}

	private String getBase64EncodedUsernamePassword() {
		String b64encoding = String.format("%s:%s", this.authenticatorConfig.getUsername(), this.authenticatorConfig.getPassword());
		
		byte[] encodedBytes = null;
		encodedBytes = b64encoding.getBytes(StandardCharsets.UTF_8);
		return Base64.getEncoder().encodeToString(encodedBytes);
	}
	
	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		String b64usernamepassword = this.getBase64EncodedUsernamePassword();
		
		httpget.setHeader("Authorization", String.format("Basic %s", b64usernamepassword));
	}

	@Override
	public Consumer<WebClient.Builder> lookupEnrichAuthentication() {
		return builder -> builder.defaultHeaders(c -> c.setBasicAuth(authenticatorConfig.getUsername(),authenticatorConfig.getPassword()));
	}

}
