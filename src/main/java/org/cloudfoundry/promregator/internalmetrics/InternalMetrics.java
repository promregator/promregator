package org.cloudfoundry.promregator.internalmetrics;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;

@Component
public class InternalMetrics {
	@Value("${promregator.metrics.internal:false}")
	private boolean enabled;
	
	private Histogram latencyCFFetch;

	@PostConstruct
	@SuppressWarnings("PMD.UnusedPrivateMethod")
	// method is required and called by the Spring Framework
	private void registerMetrics() {
		if (!this.enabled)
			return;
		
		this.latencyCFFetch = Histogram.build("promregator_cffetch_latency", "Latency on retrieving CF values")
				.labelNames("request_type").linearBuckets(0.1, 0.1, 50).register();
	}

	
	public Timer startTimerCFFetch(String requestType) {
		if (!this.enabled)
			return null;
		
		return this.latencyCFFetch.labels(requestType).startTimer();
	}

}
