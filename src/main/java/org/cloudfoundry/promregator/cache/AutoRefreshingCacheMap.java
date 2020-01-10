package org.cloudfoundry.promregator.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.collections4.map.AbstractMapDecorator;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class AutoRefreshingCacheMap<K, V> extends AbstractMapDecorator<K, V> {
	private Duration refreshInterval;
	private Duration expiryDuration;
	private Function<K, V> loaderFunction;
	private RefresherThread<K, V> refresherThread;
	
	private InternalMetrics internalMetrics;

	private static class EntryProperties {
		private Instant lastUsed;
		private Instant lastLoaded;
		private Object lockObject = new Object();
		
		public EntryProperties() {
			this.lastUsed = null;
			this.justLoaded();
		}
		
		/**
		 * @return the lastUsed
		 */
		public Instant getLastUsed() {
			return lastUsed;
		}
		/**
		 * @return the lastLoaded
		 */
		public Instant getLastLoaded() {
			return lastLoaded;
		}
		
		public void justUsed() {
			this.lastUsed = Instant.now();
		}
		
		/**
		 * may also be used to indicate a reloading/refreshing!
		 */
		public void justLoaded() {
			this.lastLoaded = Instant.now();
		}

		/**
		 * @return the lockObject
		 */
		public Object getLockObject() {
			return lockObject;
		}
		
		
	}
	
	private final Map<K, EntryProperties> entryPropertiesMap = Collections.synchronizedMap(new HashMap<>());
	private String name;
	
	public AutoRefreshingCacheMap(String cacheMapName, InternalMetrics internalMetrics, Duration expiryDuration, Duration refreshInterval, Function<K, V> loaderFunction) {
		super(Collections.synchronizedMap(new HashMap<>()));
		this.refreshInterval = refreshInterval;
		this.internalMetrics = internalMetrics;
		this.expiryDuration = expiryDuration;
		this.loaderFunction = loaderFunction;
		this.name = cacheMapName;
		
		/*
		 * Warning! Due to spring bootstrapping, this constructor can be called multiple times,
		 * which then may create "empty" instances.
		 * To prevent our statistics to be obfuscated across the instances, 
		 * we start the refresherThread only lazily: Upon the first record to be arriving at the map...
		 * See also ensureRefresherThreadIsRunning()
		 */
	}

	private synchronized void ensureRefresherThreadIsRunning() {
		if (this.refresherThread == null) {
			this.refresherThread = new RefresherThread<>(this);
			this.refresherThread.start();
		}
	}
	
	public void setRefresherThreadWithIncreasedPriority(boolean increased) {
		this.ensureRefresherThreadIsRunning();
		
		this.refresherThread.setIncreasedPriority(increased);
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		this.refresherThread.shutdown();
		super.finalize();
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.collections4.map.AbstractMapDecorator#get(java.lang.Object)
	 */
	@Override
	public V get(Object key) {
		EntryProperties ep;
		
		synchronized (this.entryPropertiesMap) {
			ep = this.entryPropertiesMap.get(key);
			if (ep == null) {
				// this creates a new EntryProperties element quite early for which there is no entry in the main map yet.
				ep = new EntryProperties();
				this.entryPropertiesMap.put((K) key, ep);
			} else {
				ep.justUsed();
			}
		}
		
		Object lockObject = ep.getLockObject();
		
		V value = null;
		synchronized (lockObject) {
			value = super.get(key);
			if (value != null) {
				// cache hit
				return value;
			}
			
			// cache miss
			value = this.loaderFunction.apply((K) key);
			if (value != null) {
				this.put((K) key, value);
				
				this.entryPropertiesMap.computeIfAbsent((K) key, k -> {
					EntryProperties epnew = new EntryProperties();
					epnew.justUsed();
					return epnew;
				});
			}
		}
		
		return value;
	}
	
	/* (non-Javadoc)
	 * @see org.apache.commons.collections4.map.AbstractMapDecorator#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V put(K key, V value) {
		this.ensureRefresherThreadIsRunning();
		
		this.touchKey(key);
		
		return super.put(key, value);
	}

	private void touchKey(K key) {
		EntryProperties ep = this.entryPropertiesMap.get(key);
		if (ep != null) {
			ep.justLoaded();
		} else {
			ep = new EntryProperties();
			this.entryPropertiesMap.put(key, ep);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.apache.commons.collections4.map.AbstractMapDecorator#putAll(java.util.Map)
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> mapToCopy) {
		this.ensureRefresherThreadIsRunning();

		for (K key : mapToCopy.keySet()) {
			this.touchKey(key);
		}
		
		super.putAll(mapToCopy);
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.collections4.map.AbstractMapDecorator#remove(java.lang.Object)
	 */
	@Override
	public V remove(Object key) {
		if (this.entryPropertiesMap.containsKey(key)) {
			this.entryPropertiesMap.remove(key);
		}
		return super.remove(key);
	}
	
	/* (non-Javadoc)
	 * @see org.apache.commons.collections4.map.AbstractMapDecorator#clear()
	 */
	@Override
	public void clear() {
		super.clear();
		this.entryPropertiesMap.clear();
	}

	private static class RefresherThread<K, V> extends Thread {
		private static final Logger log = LoggerFactory.getLogger(RefresherThread.class);
		
		private AutoRefreshingCacheMap<K, V> map;
		
		private boolean shouldRun = true;
		
		public RefresherThread(AutoRefreshingCacheMap<K, V> map) {
			super(String.format("RefresherThread for AutoRefreshingCacheMap '%s'", map.getName()));
			this.setDaemon(true);
			this.map = map;
		}

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			MDC.put("AutoRefreshingCacheMap", this.map.getName());
			log.debug(String.format("Starting checks for this AutoRefreshingCacheMap; expiry: %dms, refresh: %dms", this.map.expiryDuration.toMillis(), this.map.refreshInterval.toMillis()));
			while (this.shouldRun) {
				try {
					this.refreshMap();
				} catch (Exception e) {
					log.warn("Unexpected exception was raised in Refresher Thread for AutoRefreshingCacheMap", e);
					// fall-through is expected!
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// received signal; check if something has to be done
					log.info("Stopping RefresherThread");
					Thread.currentThread().interrupt();
				}
			}
			log.debug("Refresher Thread for this AutoRefreshingCacheMap has shut down");
			MDC.remove("AutoRefreshingCacheMap");
		}
		
		private void refreshMap() {
			Instant expiryEntry = Instant.now().minus(this.map.expiryDuration);
			Instant refreshEntryInstant = Instant.now().minus(this.map.refreshInterval);
			
			if (this.map.internalMetrics != null) {
				this.map.internalMetrics.setTimestampAutoRefreshingCacheMapRefreshScan(this.map.getName());
				this.map.internalMetrics.setAutoRefreshingCacheMapSize(this.map.getName(), this.map.size());
			}
			
			List<K> deleteList = new LinkedList<>();
			List<K> refreshList = new LinkedList<>();
			
			synchronized (this.map.entryPropertiesMap) {
				for (Entry<K, EntryProperties> entry : this.map.entryPropertiesMap.entrySet()) {
					EntryProperties ep = entry.getValue();
					if (ep.getLastUsed() == null || ep.getLastUsed().isBefore(expiryEntry)) {
						deleteList.add(entry.getKey());
					} else if (ep.getLastLoaded().isBefore(refreshEntryInstant)) {
						refreshList.add(entry.getKey());
					}
				}
			}

			deleteEntries(deleteList);
			
			refreshEntries(refreshList);
		}

		private void refreshEntries(List<K> refreshList) {
			for (K key : refreshList) {
				log.debug(String.format("Refreshing key %s", key.toString()));
				V value = this.map.loaderFunction.apply(key);
				if (value != null) {
					this.map.put(key, value);
					if (this.map.internalMetrics != null) {
						this.map.internalMetrics.countAutoRefreshingCacheMapRefreshSuccess(this.map.getName());
					}
				} else {
					log.debug(String.format("Loader did not provide a value for key %s", key.toString()));
					if (this.map.internalMetrics != null) {
						this.map.internalMetrics.countAutoRefreshingCacheMapRefreshFailure(this.map.getName());
					}
				}
			}
		}

		private void deleteEntries(List<K> deleteList) {
			for (K key : deleteList) {
				if (this.map.internalMetrics != null) {
					this.map.internalMetrics.countAutoRefreshingCacheMapExpiry(this.map.getName());
				}
				log.debug(String.format("Deleting expired value for key %s", key));
				this.map.remove(key);
			}
		}

		private void setIncreasedPriority(boolean increased) {
			this.setPriority(increased ? Thread.NORM_PRIORITY+1 : Thread.NORM_PRIORITY);
		}
		
		/**
		 * may be called to shutdown this thread and release the resources
		 */
		public void shutdown() {
			log.debug("Shutdown was triggered");
			this.shouldRun = false;
			this.interrupt();
		}
	}
}
