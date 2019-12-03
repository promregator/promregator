package org.cloudfoundry.promregator.rewrite;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;

public abstract class AbstractMetricFamilySamplesEnricher {

	public AbstractMetricFamilySamplesEnricher() {
		super();
	}

	public Map<String, Collector.MetricFamilySamples> determineEnumerationOfMetricFamilySamples(Map<String, Collector.MetricFamilySamples> emfs) {
		
		if (emfs == null) {
			return null;
		}
		
		Map<String, Collector.MetricFamilySamples> newMap = new HashMap<>();
		
		for (Entry<String, MetricFamilySamples> entry : emfs.entrySet()) {
			MetricFamilySamples mfs = entry.getValue();
			
			List<Collector.MetricFamilySamples.Sample> newSamples = new LinkedList<>();
			for (Collector.MetricFamilySamples.Sample sample : mfs.samples) {
				Collector.MetricFamilySamples.Sample newSample = new Collector.MetricFamilySamples.Sample(
						sample.name,
						this.getEnrichedLabelNames(sample.labelNames),
						this.getEnrichedLabelValues(sample.labelValues),
						sample.value);
				newSamples.add(newSample);
			}
			
			Collector.MetricFamilySamples newEntry = new Collector.MetricFamilySamples(
					mfs.name,
					mfs.type, 
					mfs.help,
					newSamples
					);
			newMap.put(entry.getKey(), newEntry);
		}
		
		return newMap;
	}
	
	protected abstract List<String> getEnrichedLabelNames(List<String> original);
	
	public abstract List<String> getEnrichedLabelValues(List<String> original);



}