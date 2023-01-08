package org.cloudfoundry.promregator.internalmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.google.common.collect.Lists;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import io.prometheus.client.cache.caffeine.CacheMetricsCollector;

public class InternalMetrics {
	@Value("${promregator.metrics.internal:false}")
	private boolean enabled;
	
	private Histogram latencyCFFetch;
	
	private Counter connectionWatchdogReconnects;
	
	private CacheMetricsCollector caffeineCacheMetricsCollector;

	private Histogram rateLimitWaitTime;
	private AtomicInteger rateLimitQueueSize = new AtomicInteger(0);

	private Supplier<Double> dequeRouteSizeFunction;
	private Supplier<Double> dequeProcessSizeFunction;
	
	private class InternalCollector extends Collector {

		private static final String PROMREGATOR_CFFETCH_RATELIMIT_QUEUE_SIZE = "promregator_cffetch_ratelimit_queue_size";
		private static final String PROMREGATOR_REQUEST_AGGREGATOR_QUEUE_SIZE = "promregator_request_aggregator_queue_size";

		@Override
		public List<MetricFamilySamples> collect() {
			ArrayList<MetricFamilySamples> result = new ArrayList<>(2);
			
			Sample queueSize = new Sample(PROMREGATOR_CFFETCH_RATELIMIT_QUEUE_SIZE, new ArrayList<>(), new ArrayList<>(), rateLimitQueueSize.doubleValue());
			MetricFamilySamples queueSizeMFS = new MetricFamilySamples(PROMREGATOR_CFFETCH_RATELIMIT_QUEUE_SIZE, Type.GAUGE, 
					"The number of CFCC requests being throttled by rate limiting", Lists.newArrayList(queueSize));
			result.add(queueSizeMFS);
			
			if (dequeRouteSizeFunction != null && dequeProcessSizeFunction != null) {
				Sample queueSizeDequeRoute = new Sample(PROMREGATOR_REQUEST_AGGREGATOR_QUEUE_SIZE, Lists.newArrayList("type"), Lists.newArrayList("route"), dequeRouteSizeFunction.get());
				Sample queueSizeDequeProcess = new Sample(PROMREGATOR_REQUEST_AGGREGATOR_QUEUE_SIZE, Lists.newArrayList("type"), Lists.newArrayList("process"), dequeProcessSizeFunction.get());
				MetricFamilySamples queueSizeDequeMFS = new MetricFamilySamples(PROMREGATOR_REQUEST_AGGREGATOR_QUEUE_SIZE, Type.GAUGE, 
						"The number of requests in the RequestAggregator's deque", Lists.newArrayList(queueSizeDequeRoute, queueSizeDequeProcess));
				result.add(queueSizeDequeMFS);
			}
			
			
			return result;
		}
		
	}
	
	@PostConstruct
	@SuppressWarnings("PMD.UnusedPrivateMethod")
	// method is required and called by the Spring Framework
	private void registerMetrics() {
		if (!this.enabled)
			return;
		
		this.latencyCFFetch = Histogram.build("promregator_cffetch_latency", "Latency on retrieving CF values")
				.labelNames("request_type").linearBuckets(0.1, 0.1, 50).register();
		
		this.connectionWatchdogReconnects = Counter.build("promregator_connection_watchdog_reconnect", "The number of reconnection attempts made by the Connection Watchdog")
				.register();
		
		this.caffeineCacheMetricsCollector = new CacheMetricsCollector().register();
		
		this.rateLimitWaitTime = Histogram.build("promregator_cffetch_ratelimit_waittime", "Wait time due to CFCC rate limiting")
				.labelNames("request_type").linearBuckets(0.0, 0.05, 50).register();
		
		CollectorRegistry.defaultRegistry.register(new InternalCollector());
	}

	
	public Timer startTimerCFFetch(String requestType) {
		if (!this.enabled)
			return null;
		
		return this.latencyCFFetch.labels(requestType).startTimer();
	}
	
	public void countConnectionWatchdogReconnect() {
		this.connectionWatchdogReconnects.inc();
	}
	
	public int getCountConnectionWatchdogReconnect() {
		return (int) this.connectionWatchdogReconnects.get();
	}
	
	public void addCaffeineCache(String cacheName, AsyncLoadingCache<?, ?> cache) {
		if (!this.enabled)
			return;
		
		this.caffeineCacheMetricsCollector.addCache(cacheName, cache);
	}
	
	public void observeRateLimiterDuration(String requestType, double waitTime) {
		if (!this.enabled)
			return;

		this.rateLimitWaitTime.labels(requestType).observe(waitTime);
	}
	
	public void increaseRateLimitQueueSize() {
		if (!this.enabled)
			return;

		this.rateLimitQueueSize.incrementAndGet();
	}
	
	public void decreaseRateLimitQueueSize() {
		if (!this.enabled)
			return;

		this.rateLimitQueueSize.decrementAndGet();
	}
	
	public void registerDequeRouteSizeFunction(Supplier<Double> function) {
		this.dequeRouteSizeFunction = function;
	}
	
	public void registerDequeProcessSizeFunction(Supplier<Double> function) {
		this.dequeProcessSizeFunction = function;
	}

}
