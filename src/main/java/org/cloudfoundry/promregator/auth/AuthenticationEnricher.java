package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;

public interface AuthenticationEnricher {
	public void enrichWithAuthentication(HttpGet httpget);
}
