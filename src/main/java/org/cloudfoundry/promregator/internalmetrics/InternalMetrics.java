package org.cloudfoundry.promregator.internalmetrics;

import javax.annotation.PostConstruct;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Value;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import io.prometheus.client.cache.caffeine.CacheMetricsCollector;

public class InternalMetrics {
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

	@PostConstruct
	@SuppressWarnings("PMD.UnusedPrivateMethod")
	// method is required and called by the Spring Framework
	private void registerMetrics() {
		if (!this.enabled)
			return;
		
		this.latencyCFFetch = Histogram.build("promregator_cffetch_latency", "Latency on retrieving CF values")
				.labelNames("request_type").linearBuckets(0.1, 0.1, 50).register();
		
		this.autoRefreshingCacheMapSize = Gauge.build("promregator_autorefreshingcachemap_size", "The size of objects stored in an AutoRefreshingCacheMap")
				.labelNames("cache_map_name").register();
		
		this.autoRefreshingCacheMapExpiry = Counter.build("promregator_autorefreshingcachemap_expiry", "The number of objects having expired so far in an AutoRefreshingCacheMap")
				.labelNames("cache_map_name").register();
		this.autoRefreshingCacheMapRefreshSuccess = Counter.build("promregator_autorefreshingcachemap_refresh_success", "The number of successful refreshes of objact so far in an AutoRefreshingCacheMap")
				.labelNames("cache_map_name").register();
		this.autoRefreshingCacheMapRefreshFailure = Counter.build("promregator_autorefreshingcachemap_refresh_failure", "The number of failed refreshes of objact so far in an AutoRefreshingCacheMap")
				.labelNames("cache_map_name").register();
		
		this.autoRefreshingCacheMapErroneousEntryDisplaced = Counter.build("promregator_autorefreshingcachemap_erroneous_entry_displaced", "The number of cache items displaced in an AutoRefreshingCacheMap, because they were detected to be erroneous")
				.labelNames("cache_map_name").register();

		this.autoRefreshingCacheMapLastScan = Gauge.build("promregator_autorefreshingcachemap_scantimestamp", "The timestamp of the last execution of the RefreshThread execution of an AutoRefreshingCacheMap")
				.labelNames("cache_map_name").register();
		
		this.connectionWatchdogReconnects = Counter.build("promregator_connection_watchdog_reconnect", "The number of reconnection attempts made by the Connection Watchdog")
				.register();
		
		this.caffeineCacheMetricsCollector = new CacheMetricsCollector().register();
		
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
	
	public void addCaffeineCache(String cacheName, Cache<?, ?> cache) {
		if (!this.enabled)
			return;
		
		this.caffeineCacheMetricsCollector.addCache(cacheName, cache);
	}
}
