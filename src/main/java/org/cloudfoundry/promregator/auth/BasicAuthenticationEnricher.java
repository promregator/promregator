package org.cloudfoundry.promregator.auth;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;

public class BasicAuthenticationEnricher implements AuthenticationEnricher {
	
	private static final Logger log = Logger.getLogger(BasicAuthenticationEnricher.class);
	
	private final BasicAuthenticationConfiguration authenticatorConfig;
	
	public BasicAuthenticationEnricher(BasicAuthenticationConfiguration authenticatorConfig) {
		super();
		this.authenticatorConfig = authenticatorConfig;
	}

	private String getBase64EncodedUsernamePassword() {
		String b64encoding = String.format("%s:%s", this.authenticatorConfig.getUsername(), this.authenticatorConfig.getPassword());
		
		byte[] encodedBytes = null;
		try {
			encodedBytes = b64encoding.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.error("Unable to b64-encode using UTF-8", e);
			return null;
		}
		String encoding = Base64.getEncoder().encodeToString(encodedBytes);
		return encoding;
	}
	
	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		String b64usernamepassword = this.getBase64EncodedUsernamePassword();
		
		httpget.setHeader("Authorization", String.format("Basic %s", b64usernamepassword));
	}

}
