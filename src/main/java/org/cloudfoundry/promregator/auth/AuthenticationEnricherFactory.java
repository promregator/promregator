package org.cloudfoundry.promregator.auth;

import org.cloudfoundry.promregator.config.AuthenticatorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationEnricherFactory {
	private static final Logger log = LoggerFactory.getLogger(AuthenticationEnricherFactory.class);
	
	private AuthenticationEnricherFactory() {
		// left blank intentionally
	}
	
	public static AuthenticationEnricher create(AuthenticatorConfiguration authConfig) {
		AuthenticationEnricher ae = null;
		
		String type = authConfig.getType();
		if("OAuth2XSUAA".equalsIgnoreCase(type)) {
			log.warn("You are using deprecated authentication configuration type '{}'. Switch to '{}' instead.", type, "OAuth2XSUAABasic");
			ae = new OAuth2XSUAAEnricher(authConfig.getOauth2xsuaa());
		} else if ("OAuth2XSUAABasic".equalsIgnoreCase(type)) {
			ae = new OAuth2XSUAAEnricher(authConfig.getOauth2xsuaaBasic());
		} else if ("OAuth2XSUAACertificate".equalsIgnoreCase(authConfig.getType())) {
			ae = new OAuth2XSUAAEnricher(authConfig.getOauth2xsuaaCertificate());
		} else if ("none".equalsIgnoreCase(type) || "null".equalsIgnoreCase(type)) {
			ae = new NullEnricher();
		} else if ("basic".equalsIgnoreCase(type)) {
			ae = new BasicAuthenticationEnricher(authConfig.getBasic());
		} else {
			log.warn(String.format("Authenticator type %s is unknown; skipping", type));
		}

		return ae;
	}
}
