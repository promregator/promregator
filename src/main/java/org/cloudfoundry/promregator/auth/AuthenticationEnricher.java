package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;

public interface AuthenticationEnricher {
	void enrichWithAuthentication(HttpGet httpget);
}
