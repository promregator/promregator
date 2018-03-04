package org.cloudfoundry.promregator.internalmetrics;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.prometheus.client.Counter;

@Component
public class InternalMetrics {
	private Counter cacheHits;
	private Counter cacheMiss;

	@Value("${promregator.metrics.internal:false}")
	private boolean enabled;
	
	@PostConstruct
	private void registerMetrics() {
		if (!this.enabled)
			return;
		
		this.cacheHits = Counter.build("promregator_cache_hits", "Hits on caches of Promregator")
				.labelNames("cache").register();
		
		this.cacheMiss= Counter.build("promregator_cache_miss", "Misses on caches of Promregator")
				.labelNames("cache").register();
	}
	
	public void countHit(String cacheName) {
		if (!this.enabled)
			return;
		
		cacheHits.labels(cacheName).inc();
	}

	public void countMiss(String cacheName) {
		if (!this.enabled)
			return;
		
		cacheMiss.labels(cacheName).inc();
	}

}
