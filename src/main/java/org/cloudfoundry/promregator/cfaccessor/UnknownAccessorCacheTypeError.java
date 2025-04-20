package org.cloudfoundry.promregator.cfaccessor;

import java.io.Serial;

public class UnknownAccessorCacheTypeError extends Error {

	@Serial
	private static final long serialVersionUID = 2033158105309893351L;

	public UnknownAccessorCacheTypeError(String arg0) {
		super(arg0);
	}
	
}
