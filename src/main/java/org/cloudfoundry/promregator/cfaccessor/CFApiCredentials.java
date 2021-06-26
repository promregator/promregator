package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.promregator.meta.Released;

@Released(since="0.10.0")
public interface CFApiCredentials {
	String getUsername();
	String getPassword();
}
