package org.cloudfoundry.promregator.rewrite;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import io.prometheus.client.Collector.MetricFamilySamples;

public class MergableMetricFamilySamples {
	
	private static final Logger log = Logger.getLogger(MergableMetricFamilySamples.class);
	
	private HashMap<String, MetricFamilySamples> map = new HashMap<>();
	
	public MergableMetricFamilySamples() {
		super();
	}
	
	public void merge (Enumeration<MetricFamilySamples> emfs) {
		HashMap<String, MetricFamilySamples> others = MFSUtils.convertToEMFSToHashMap(emfs);
		
		this.merge(others);
	}
	
	public void merge(HashMap<String,MetricFamilySamples> others) {
		for (Entry<String, MetricFamilySamples> entry : others.entrySet()) {
			String metricName = entry.getKey();
			MetricFamilySamples otherMFS = entry.getValue();
			
			MetricFamilySamples mfs = this.map.get(metricName);
			if (mfs == null) {
				this.map.put(metricName, otherMFS);
				continue;
			}
			
			if (otherMFS.type != mfs.type) {
				log.warn(String.format("Attempt to merge metric %s, but types are deviating: %s vs. %s", metricName, otherMFS.toString(), mfs.toString()));
				continue;
			}
			
			mfs.samples.addAll(otherMFS.samples);
		}
	}
	
	public Enumeration<MetricFamilySamples> getEnumerationMetricFamilySamples() {
		return Collections.enumeration(this.map.values());
	}
	
	public HashMap<String,MetricFamilySamples> getEnumerationMetricFamilySamplesInHashMap() {
		return new HashMap<>(this.map);
		// NB: This is not a deep clone, but only a shallow one!
	}
	
}
