package org.cloudfoundry.promregator.auth;

import org.apache.hc.client5.http.classic.methods.HttpGet;

public interface AuthenticationEnricher {
	void enrichWithAuthentication(HttpGet httpget);
}
