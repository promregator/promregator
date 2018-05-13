package org.cloudfoundry.promregator.fetcher;

import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

/**
 * A class storing information about metrics, which are being used to measure
 * the behavior of MetricsFetchers
 */
public class MetricsFetcherMetrics {
	/* references to metrics which we create and expose by our own */
	
	private static Histogram requestLatency = Histogram.build("promregator_request_latency", "The latency, which the targets of the promregator produce")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	private boolean requestLatencyEnabled;
	
	private static Counter failedRequests = Counter.build("promregator_request_failure", "Requests, which responded, but the HTTP code indicated an error or the connection dropped/timed out")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	
	private static Histogram requestSize = Histogram.build("promregator_request_size", "The size in bytes of the document, which the scraped targets sent to promregator")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.exponentialBuckets(100, 1.5, 16)
			.register();

	private String[] ownTelemetryLabels;

	public MetricsFetcherMetrics(String[] ownTelemetryLabels, boolean requestLatencyEnabled) {
		super();
		
		this.ownTelemetryLabels = ownTelemetryLabels.clone();
		this.requestLatencyEnabled = requestLatencyEnabled;
	}

	public String[] getOwnTelemetryLabels() {
		return ownTelemetryLabels.clone();
	}

	public Histogram.Child getLatencyRequest() {
		if (requestLatency == null)
			return null;
		
		if (!this.requestLatencyEnabled)
			return null;
		
		return requestLatency.labels(this.ownTelemetryLabels);
	}

	public Counter.Child getFailedRequests() {
		if (failedRequests == null)
			return null;
		
		return failedRequests.labels(this.ownTelemetryLabels);
	}
	
	public Histogram.Child getRequestSize() {
		if (requestSize == null)
			return null;
		
		return requestSize.labels(this.ownTelemetryLabels);
	}
	
	
	/**
	 * deregisters the samples from the (global) CollectorRegistry
	 * once an instance is no longer required.
	 */
	public void deregisterSamplesFromRegistry() {
		/*
		 * Note that we do not need to de-register the Up metric:
		 * - up metrics are registered a request-level registry only, which gets destroyed automatically
		 * - we here only deal with samples coming from metrics, which are registered at the global CollectorRegistry
		 */
		
		requestLatency.remove(this.ownTelemetryLabels);
		failedRequests.remove(this.ownTelemetryLabels);
		requestSize.remove(this.ownTelemetryLabels);
	}
}
