package org.cloudfoundry.promregator.cache;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.commons.collections4.map.AbstractMapDecorator;

public class AutoRefreshingCacheMap<K, V> extends AbstractMapDecorator<K, V> {

	private TimeUnit refreshInterval;
	private Function<K, V> loaderFunction;

	private static class EntryProperties {
		private Instant lastUsed;
		private Instant lastLoaded;
		
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
	}
	
	private Map<K, EntryProperties> entryPropertiesMap = Collections.synchronizedMap(new HashMap<K, EntryProperties>());
	
	public AutoRefreshingCacheMap(TimeUnit refreshInterval, Function<K, V> loaderFunction) {
		super(Collections.synchronizedMap(new HashMap<K, V>()));
		this.refreshInterval = refreshInterval;
		this.loaderFunction = loaderFunction;
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.collections4.map.AbstractMapDecorator#get(java.lang.Object)
	 */
	@Override
	public V get(Object key) {
		EntryProperties ep = this.entryPropertiesMap.get(key);
		if (ep != null) {
			ep.justUsed();
		}
		
		return super.get(key);
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.collections4.map.AbstractMapDecorator#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V put(K key, V value) {
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
	

}
