package org.cloudfoundry.promregator.rewrite;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.exporter.common.TextFormat;

public class MergableMetricFamilySamples {
	
	private static final Logger log = Logger.getLogger(MergableMetricFamilySamples.class);
	
	private Map<String, MetricFamilySamples> map = new HashMap<>();
	
	public MergableMetricFamilySamples() {
		super();
	}
	
	public void merge (Enumeration<MetricFamilySamples> emfs) {
		Map<String, MetricFamilySamples> others = MFSUtils.convertToEMFSToHashMap(emfs);
		
		this.merge(others);
	}
	
	public void merge(Map<String,MetricFamilySamples> others) {
		for (Entry<String, MetricFamilySamples> entry : others.entrySet()) {
			String metricName = entry.getKey();
			MetricFamilySamples otherMFS = entry.getValue();
			
			MetricFamilySamples mfs = this.map.computeIfAbsent(metricName,k-> otherMFS);
			
			if (otherMFS.type != mfs.type) {
				final String logmsg = String.format("Scraping resulted in a collision of metric types for metric with name %s. "
						+ "The conflicting types provided were %s and %s. The data with type %s is kept, while the other metrics values will be dropped. "
						+ "For further details see also https://github.com/promregator/promregator/wiki/Multiple-Type-Declarations-of-a-Metric-Cause-a-Collision-in-Single-Endpoint-Scraping-Mode ; "
						+ "Details: metric data already stored: %s --- metric data requested to be merged, but failed to do so: %s", 
						metricName, mfs.type.toString(), otherMFS.type.toString(), mfs.type.toString(), mfs.toString(), otherMFS.toString());
				log.warn(logmsg);
				continue;
			}
			
			mfs.samples.addAll(otherMFS.samples);
		}
	}
	
	public Enumeration<MetricFamilySamples> getEnumerationMetricFamilySamples() {
		Collection<MetricFamilySamples> coll = this.map.values();
		
		for (Iterator<MetricFamilySamples> iterator = coll.iterator(); iterator.hasNext();) {
			MetricFamilySamples mfs = iterator.next();
			if (mfs.type == Type.UNTYPED) {
				log.warn(String.format("Dropping metric %s from set of metrics, as it is untyped and the simpleclient's serialization coding does not properly support this", mfs.name));
				iterator.remove();
			}
		}
		
		return Collections.enumeration(coll);
	}
	
	public Map<String,MetricFamilySamples> getEnumerationMetricFamilySamplesInHashMap() {
		return new HashMap<>(this.map);
		// NB: This is not a deep clone, but only a shallow one!
	}
	
	public String toType004String() {
		Enumeration<MetricFamilySamples> resultEMFS = this.getEnumerationMetricFamilySamples();
		Writer writer = new StringWriter();
		try {
			TextFormat.write004(writer, resultEMFS);
		} catch (IOException e) {
			log.error("IO Exception on StringWriter; uuuhhh...", e);
		}
		
		return writer.toString();
	}
	
}
