package org.cloudfoundry.promregator.config;

import java.io.Serial;

public class InvalidTargetProtocolSpecifiedError extends Error {

	@Serial
	private static final long serialVersionUID = -1808726562105315177L;

	public InvalidTargetProtocolSpecifiedError(String message) {
		super(message);
	}
}
