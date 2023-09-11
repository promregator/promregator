package org.cloudfoundry.promregator.auth;

import org.apache.hc.client5.http.classic.methods.HttpGet;

/**
 * The NullEnricher is an AuthenticationEnricher, which does not enrich 
 * the HTTP request. It can be used in case an AuthenticationEnricher is required,
 * but no operation shall be performed
 *
 */
public class NullEnricher implements AuthenticationEnricher {

	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		// left blank intentionally
	}

}
