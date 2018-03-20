package org.cloudfoundry.promregator.fetcher;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

/**
 * A class storing information about metrics, which are being used to measure
 * the behaviour of MetricsFetchers
 */
public class MetricsFetcherMetrics {
	/* references to metrics which we create and expose by our own */
	private Histogram latencyRequest;
	private Gauge up;
	private Counter failedRequests;


	private String[] ownTelemetryLabels;
	
	public MetricsFetcherMetrics(AbstractMetricFamilySamplesEnricher mfse, 
			Histogram latencyRequest, Gauge up, Counter failedRequests) {
		super();
		
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		this.ownTelemetryLabels = labelValues.toArray(new String[0]);

		this.latencyRequest = latencyRequest;
		this.up = up;
		this.failedRequests = failedRequests;
	}

	public String[] getOwnTelemetryLabels() {
		return ownTelemetryLabels.clone();
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
