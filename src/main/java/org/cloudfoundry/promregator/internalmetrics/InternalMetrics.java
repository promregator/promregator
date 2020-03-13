package org.cloudfoundry.promregator.internalmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.google.common.collect.Lists;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import io.prometheus.client.cache.caffeine.CacheMetricsCollector;

public class InternalMetrics {
	private static final String CACHE_MAP_NAME = "cache_map_name";

	@Value("${promregator.metrics.internal:false}")
	private boolean enabled;
	
	private Histogram latencyCFFetch;
	
	private Gauge autoRefreshingCacheMapSize;
	private Counter autoRefreshingCacheMapExpiry;
	private Counter autoRefreshingCacheMapRefreshSuccess;
	private Counter autoRefreshingCacheMapRefreshFailure;
	private Counter autoRefreshingCacheMapErroneousEntryDisplaced;
	private Gauge autoRefreshingCacheMapLastScan;
	
	private Counter connectionWatchdogReconnects;
	
	private CacheMetricsCollector caffeineCacheMetricsCollector;

	private Histogram rateLimitWaitTime;
	private AtomicInteger rateLimitQueueSize = new AtomicInteger(0);
	
	private class InternalCollector extends Collector {

		private static final String PROMREGATOR_CFFETCH_RATELIMIT_QUEUE_SIZE = "promregator_cffetch_ratelimit_queue_size";

		@Override
		public List<MetricFamilySamples> collect() {
			
			Sample queueSize = new Sample(PROMREGATOR_CFFETCH_RATELIMIT_QUEUE_SIZE, new ArrayList<>(), new ArrayList<>(), rateLimitQueueSize.doubleValue());
			
			MetricFamilySamples queueSizeMFS = new MetricFamilySamples(PROMREGATOR_CFFETCH_RATELIMIT_QUEUE_SIZE, Type.GAUGE, 
					"The number of CFCC requests being throttled by rate limiting", Lists.newArrayList(queueSize));
			
			return Lists.newArrayList(queueSizeMFS);
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
		
		this.autoRefreshingCacheMapSize = Gauge.build("promregator_autorefreshingcachemap_size", "The size of objects stored in an AutoRefreshingCacheMap")
				.labelNames(CACHE_MAP_NAME).register();
		
		this.autoRefreshingCacheMapExpiry = Counter.build("promregator_autorefreshingcachemap_expiry", "The number of objects having expired so far in an AutoRefreshingCacheMap")
				.labelNames(CACHE_MAP_NAME).register();
		this.autoRefreshingCacheMapRefreshSuccess = Counter.build("promregator_autorefreshingcachemap_refresh_success", "The number of successful refreshes of object so far in an AutoRefreshingCacheMap")
				.labelNames(CACHE_MAP_NAME).register();
		this.autoRefreshingCacheMapRefreshFailure = Counter.build("promregator_autorefreshingcachemap_refresh_failure", "The number of failed refreshes of object so far in an AutoRefreshingCacheMap")
				.labelNames(CACHE_MAP_NAME).register();
		
		this.autoRefreshingCacheMapErroneousEntryDisplaced = Counter.build("promregator_autorefreshingcachemap_erroneous_entry_displaced", "The number of cache items displaced in an AutoRefreshingCacheMap, because they were detected to be erroneous")
				.labelNames(CACHE_MAP_NAME).register();

		this.autoRefreshingCacheMapLastScan = Gauge.build("promregator_autorefreshingcachemap_scantimestamp", "The timestamp of the last execution of the RefreshThread execution of an AutoRefreshingCacheMap")
				.labelNames(CACHE_MAP_NAME).register();
		
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

	public void setAutoRefreshingCacheMapSize(String cacheMapName, long size) {
		if (!this.enabled)
			return;
		
		this.autoRefreshingCacheMapSize.labels(cacheMapName).set(size);
	}
	
	public void countAutoRefreshingCacheMapExpiry(String cacheMapName) {
		if (!this.enabled)
			return;
		
		this.autoRefreshingCacheMapExpiry.labels(cacheMapName).inc();
	}
	
	public void countAutoRefreshingCacheMapRefreshSuccess(String cacheMapName) {
		if (!this.enabled)
			return;
		
		this.autoRefreshingCacheMapRefreshSuccess.labels(cacheMapName).inc();
	}
	
	public void countAutoRefreshingCacheMapRefreshFailure(String cacheMapName) {
		if (!this.enabled)
			return;
		
		this.autoRefreshingCacheMapRefreshFailure.labels(cacheMapName).inc();
	}
	
	public void countAutoRefreshingCacheMapErroneousEntriesDisplaced(String cacheMapName) {
		if (!this.enabled)
			return;
		
		this.autoRefreshingCacheMapErroneousEntryDisplaced.labels(cacheMapName).inc();
	}
	
	public void setTimestampAutoRefreshingCacheMapRefreshScan(String cacheMapName) {
		if (!this.enabled)
			return;
		
		this.autoRefreshingCacheMapLastScan.labels(cacheMapName).setToCurrentTime();
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
		this.rateLimitWaitTime.labels(requestType).observe(waitTime);
		
	}
	
	public void increaseRateLimitQueueSize() {
		this.rateLimitQueueSize.incrementAndGet();
	}
	
	public void decreaseRateLimitQueueSize() {
		this.rateLimitQueueSize.decrementAndGet();
	}

}
