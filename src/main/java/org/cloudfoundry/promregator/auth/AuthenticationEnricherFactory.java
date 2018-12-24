package org.cloudfoundry.promregator.auth;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.AuthenticatorConfiguration;

public class AuthenticationEnricherFactory {
	private static final Logger log = Logger.getLogger(AuthenticationEnricherFactory.class);
	
	public static AuthenticationEnricher create(AuthenticatorConfiguration authConfig, String proxyHost, int proxyPort) {
		AuthenticationEnricher ae = null;
		
		String type = authConfig.getType();
		if ("OAuth2XSUAA".equalsIgnoreCase(type)) {
			ae = new OAuth2XSUAAEnricher(authConfig.getOauth2xsuaa(), proxyHost, proxyPort);
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
