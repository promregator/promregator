package org.cloudfoundry.promregator.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;

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
	public String enrichWithAuthentication() {
		final String b64usernamepassword = this.getBase64EncodedUsernamePassword();
		
		return String.format("Basic %s", b64usernamepassword);
	}

}
