package org.cloudfoundry.promregator.endpoint;

public class LoopbackScrapingDetectedException extends RuntimeException {

	private static final long serialVersionUID = -8088563578585318179L;

	public LoopbackScrapingDetectedException(String message) {
		super(message);
	}
}
