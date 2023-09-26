package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;

/*
 * Note: This is also a candidate for @Released
 * However, as we cannot ensure that we will stick with HttpGet
 * as client, we should not expose it in a released interface.
 * Hence, we need some adapting proxy here before we may release it.
 */
public interface AuthenticationEnricher {
	void enrichWithAuthentication(HttpGet httpget);
}
