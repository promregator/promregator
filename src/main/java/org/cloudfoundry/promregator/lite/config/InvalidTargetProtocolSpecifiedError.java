package org.cloudfoundry.promregator.lite.config;

public class InvalidTargetProtocolSpecifiedError extends Error {
	
	private static final long serialVersionUID = -1808726562105315177L;

	public InvalidTargetProtocolSpecifiedError(String message) {
		super(message);
	}
}
