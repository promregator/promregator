package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.promregator.meta.Released;

/**
 * Interface to derive username and password used for accessing the CF API 
 * programmatically.
 * 
 * An extension may implement this interface to provide username and password
 * each time Promregator requires the credentials for authenticating towards
 * the associated Cloud Foundry API server.
 * The derivation may happen dynamically (i.e. the credentials may change over
 * time).
 * It is considered an implementation detail of the extension how the 
 * credentials are being determined, e.g. read from a vault or generated
 * based on a certain schema / private key.
 */
@Released(since="0.10.0")
public interface CFApiCredentials {
	String getUsername();
	String getPassword();
}
