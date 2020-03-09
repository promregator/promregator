package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.scanner.Instance;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

public class InstanceCache {

	private Cache<String, Instance> cache;

	public InstanceCache() {
		Scheduler caffeineScheduler = Scheduler.forScheduledExecutorService(new ScheduledThreadPoolExecutor(1));
		this.cache = Caffeine.newBuilder()
			.expireAfterAccess(Duration.ofHours(1))
			.scheduler(caffeineScheduler)
			.recordStats()
			.build();
	}
	
	public void loadCache(List<Instance> newInstances) {
		for (Instance instance : newInstances) {
			String hash = instance.getHash();
			this.cache.put(hash, instance);
		}
	}


	public Instance getCachedInstance(String key) {
		return this.cache.getIfPresent(key);
	}
	
}
