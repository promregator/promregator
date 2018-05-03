package org.cloudfoundry.promregator.fetcher;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

/**
 * A class storing information about metrics, which are being used to measure
 * the behaviour of MetricsFetchers
 */
public class MetricsFetcherMetrics {
	/* references to metrics which we create and expose by our own */
	
	private static Histogram requestLatency = Histogram.build("promregator_request_latency", "The latency, which the targets of the promregator produce")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	
	private static Counter failedRequests = Counter.build("promregator_request_failure", "Requests, which responded, but the HTTP code indicated an error or the connection dropped/timed out")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();

	private Gauge up;

	private String[] ownTelemetryLabels;
	
	public MetricsFetcherMetrics(AbstractMetricFamilySamplesEnricher mfse, Gauge up) {
		super();
		
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		this.ownTelemetryLabels = labelValues.toArray(new String[0]);

		this.up = up;
	}

	public String[] getOwnTelemetryLabels() {
		return ownTelemetryLabels.clone();
	}

	public Histogram.Child getLatencyRequest() {
		if (requestLatency == null)
			return null;
		
		return requestLatency.labels(this.ownTelemetryLabels);
	}

	public Gauge.Child getUp() {
		if (this.up == null)
			return null;
		
		return this.up.labels(this.ownTelemetryLabels);
	}

	public Counter.Child getFailedRequests() {
		if (failedRequests == null)
			return null;
		
		return failedRequests.labels(this.ownTelemetryLabels);
	}

}
