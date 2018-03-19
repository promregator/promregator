package org.cloudfoundry.promregator.fetcher;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class MetricFetcherMetrics {
	/* references to metrics which we create and expose by our own */
	private String[] ownTelemetryLabels;
	private Histogram latencyRequest;
	private Gauge up;
	private Counter failedRequests;
	
	public MetricFetcherMetrics(String[] ownTelemetryLabels, Histogram latencyRequest, Gauge up,
			Counter failedRequests) {
		super();
		this.ownTelemetryLabels = ownTelemetryLabels;
		this.latencyRequest = latencyRequest;
		this.up = up;
		this.failedRequests = failedRequests;
	}

	public String[] getOwnTelemetryLabels() {
		return ownTelemetryLabels;
	}

	public Histogram.Child getLatencyRequest() {
		if (this.latencyRequest == null)
			return null;
		
		return this.latencyRequest.labels(this.ownTelemetryLabels);
	}

	public Gauge.Child getUp() {
		if (this.up == null)
			return null;
		
		return this.up.labels(this.ownTelemetryLabels);
	}

	public Counter.Child getFailedRequests() {
		if (this.failedRequests == null)
			return null;
		
		return this.failedRequests.labels(this.ownTelemetryLabels);
	}

}
