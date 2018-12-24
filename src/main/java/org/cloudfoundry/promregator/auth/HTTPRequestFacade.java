package org.cloudfoundry.promregator.auth;

public interface HTTPRequestFacade {
	void addHeader(String name, String value);
}
