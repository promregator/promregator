package org.cloudfoundry.promregator.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;

public class BasicAuthenticationEnricher implements AuthenticationEnricher {
	
	private final BasicAuthenticationConfiguration authenticatorConfig;
	
	public BasicAuthenticationEnricher(BasicAuthenticationConfiguration authenticatorConfig) {
		super();
		this.authenticatorConfig = authenticatorConfig;
	}

	private String getBase64EncodedUsernamePassword() {
		String b64encoding = "%s:%s".formatted(this.authenticatorConfig.getUsername(), this.authenticatorConfig.getPassword());
		
		byte[] encodedBytes = null;
		encodedBytes = b64encoding.getBytes(StandardCharsets.UTF_8);
		return Base64.getEncoder().encodeToString(encodedBytes);
	}
	
	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		String b64usernamepassword = this.getBase64EncodedUsernamePassword();
		
		httpget.setHeader("Authorization", "Basic %s".formatted(b64usernamepassword));
	}

}
