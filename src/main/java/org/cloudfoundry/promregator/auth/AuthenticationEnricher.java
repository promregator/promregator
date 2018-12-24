package org.cloudfoundry.promregator.auth;

public interface AuthenticationEnricher {
	void enrichWithAuthentication(HTTPRequestFacade facade);
}
