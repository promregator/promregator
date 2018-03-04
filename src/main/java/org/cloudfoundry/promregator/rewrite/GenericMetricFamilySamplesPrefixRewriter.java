package org.cloudfoundry.promregator.rewrite;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;

public class GenericMetricFamilySamplesPrefixRewriter {

	private String requiredPrefix;

	public GenericMetricFamilySamplesPrefixRewriter(String requiredPrefix) {
		if (requiredPrefix.endsWith("_")) {
			this.requiredPrefix = requiredPrefix;
		} else {
			this.requiredPrefix = requiredPrefix+"_";
		}
	}

	public HashMap<String, Collector.MetricFamilySamples> determineEnumerationOfMetricFamilySamples(HashMap<String, Collector.MetricFamilySamples> emfs) {
		
		if (emfs == null) {
			return null;
		}
		
		HashMap<String, Collector.MetricFamilySamples> newMap = new HashMap<String, Collector.MetricFamilySamples>();
		
		for (Entry<String, MetricFamilySamples> entry : emfs.entrySet()) {
			MetricFamilySamples mfs = entry.getValue();
			
			List<Collector.MetricFamilySamples.Sample> newSamples = new LinkedList<Collector.MetricFamilySamples.Sample>();
			for (Collector.MetricFamilySamples.Sample sample : mfs.samples) {
				Collector.MetricFamilySamples.Sample newSample = new Collector.MetricFamilySamples.Sample(
						this.ensureWithPrefix(sample.name),
						sample.labelNames,
						sample.labelValues,
						sample.value);
				newSamples.add(newSample);
			}
			
			Collector.MetricFamilySamples newEntry = new Collector.MetricFamilySamples(
					this.ensureWithPrefix(mfs.name),
					mfs.type, 
					mfs.help,
					newSamples
					);
			newMap.put(this.ensureWithPrefix(entry.getKey()), newEntry);
		}
		
		return newMap;
	}
	
	private String ensureWithPrefix (String identifier) {
		if (identifier == null)
			return this.requiredPrefix;
		
		if (identifier.startsWith(this.requiredPrefix)) 
			return identifier; 
		
		return this.requiredPrefix+identifier;
	}

}