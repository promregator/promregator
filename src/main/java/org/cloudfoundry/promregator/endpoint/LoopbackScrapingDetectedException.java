package org.cloudfoundry.promregator.endpoint;

import java.io.Serial;

public class LoopbackScrapingDetectedException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = -8088563578585318179L;

	public LoopbackScrapingDetectedException(String message) {
		super(message);
	}
}
