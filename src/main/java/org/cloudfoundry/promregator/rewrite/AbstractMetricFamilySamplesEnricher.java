package org.cloudfoundry.promregator.rewrite;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;

public abstract class AbstractMetricFamilySamplesEnricher {

	public AbstractMetricFamilySamplesEnricher() {
		super();
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