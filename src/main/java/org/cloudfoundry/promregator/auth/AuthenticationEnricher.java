package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;

public interface AuthenticationEnricher {
	default void enrichWithAuthentication(HttpGet httpget) {
		final String authenticationString = this.enrichWithAuthentication();
		
		if (authenticationString != null) {
			httpget.setHeader("Authorization", authenticationString);
		}
	};
	
	/**
	 * provides an authentication string that can be 
	 * added as HTTP header attribute using key "Authentication"
	 * to an HTTP request.
	 * @return the string that shall be used as value of key "Authentication". 
	 * If <code>null</code>, the header line will be suppressed completely.
	 */
	String enrichWithAuthentication();
}
