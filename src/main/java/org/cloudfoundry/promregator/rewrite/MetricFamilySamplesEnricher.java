package org.cloudfoundry.promregator.rewrite;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;

public class MetricFamilySamplesEnricher {
	private static final String LABELNAME_ORGNAME = "org_name";
	private static final String LABELNAME_SPACENAME = "space_name";
	private static final String LABELNAME_APPNAME = "app_name";
	private static final String LABELNAME_INSTANCE = "instance";
	
	private String orgName;
	private String spaceName;
	private String appName;
	private String instance;

	public MetricFamilySamplesEnricher(String orgName, String spaceName, String appName, String instance) {
		this.instance = instance;
		this.spaceName = spaceName;
		this.appName = appName;
		this.orgName = orgName;
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
	
	private List<String> getEnrichedLabelNames(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add(LABELNAME_ORGNAME);
		clone.add(LABELNAME_SPACENAME);
		clone.add(LABELNAME_APPNAME);
		clone.add(LABELNAME_INSTANCE);
		
		return clone;
	}
	
	private List<String> getEnrichedLabelValues(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add(this.orgName);
		clone.add(this.spaceName);
		clone.add(this.appName);
		clone.add(this.instance);
		
		return clone;
	}
}
