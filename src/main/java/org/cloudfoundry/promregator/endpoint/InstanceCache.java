package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

public class InstanceCache {

	private Scheduler caffeineScheduler = Scheduler.forScheduledExecutorService(new ScheduledThreadPoolExecutor(1));
	private Cache<String, Instance> cache;
	private Object cacheLock = new Object();
	
	@Autowired
	private CFDiscoverer discoverer;
	
	public InstanceCache() {
		this.cache = Caffeine.newBuilder()
			.expireAfterAccess(Duration.ofHours(1))
			.scheduler(this.caffeineScheduler)
			.recordStats()
			.build();
	}
	
	private void loadCache() {
		/* Note that this method is only called, if we are synchronized to cacheLock */
		List<Instance> listInstances = this.getAllInstances();
		
		for (Instance instance : listInstances) {
			String hash = instance.getHash();
			this.cache.put(hash, instance);
		}
		
	}
	
	private List<Instance> getAllInstances() {
		return this.discoverer.discover(null, null);
	}

	public Instance getCachedInstance(String key) {
		Instance value = this.cache.getIfPresent(key);
		if (value != null) {
			return value;
		}
		
		synchronized(this.cacheLock) {
			value = this.cache.getIfPresent(key);
			if (value != null) {
				return value;
			}
			
			this.loadCache();
		}
		
		value = this.cache.getIfPresent(key);
		return value;
	}
	
}
