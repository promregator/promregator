package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;

import io.prometheus.client.Histogram.Timer;

public class ReactiveTimer {
	private Timer t;
	private final InternalMetrics im;
	private final String requestType;
	
	public ReactiveTimer(final InternalMetrics im, final String requestType) {
		this.im = im;
		this.requestType = requestType;
	}
	
	public void start() {
		this.t = this.im.startTimerCFFetch(this.requestType);
	}

	public void stop() {
		if (this.t != null) {
			this.t.observeDuration();
		}
	}
}