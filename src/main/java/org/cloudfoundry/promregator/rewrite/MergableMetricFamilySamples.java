package org.cloudfoundry.promregator.rewrite;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.exporter.common.TextFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergableMetricFamilySamples {
	
	private static final Logger log = LoggerFactory.getLogger(MergableMetricFamilySamples.class);
	
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
				// Trivial case to merge: Only available at one side
				this.map.put(metricName, otherMFS);
			} else {
				// Non-trivial case: Both sides have values; check that types are not conflicting
				if (otherMFS.type != mfs.type) {
					log.warn("Scraping resulted in a collision of metric types for metric with name {}. "
							+ "The conflicting types provided were {} and {}. The data with type {} is kept, while the other metrics values will be dropped. "
							+ "For further details see also https://github.com/promregator/promregator/wiki/Multiple-Type-Declarations-of-a-Metric-Cause-a-Collision-in-Single-Endpoint-Scraping-Mode ; "
							+ "Details: metric data already stored: {} --- metric data requested to be merged, but failed to do so: {}", metricName, mfs.type.toString(), otherMFS.type.toString(), mfs.type.toString(), mfs.toString(), otherMFS.toString());
					continue;
				}
				
				mfs.samples.addAll(otherMFS.samples);
			}
		}
	}
	
	public Enumeration<MetricFamilySamples> getEnumerationMetricFamilySamples() {
		Collection<MetricFamilySamples> coll = this.map.values();
		
		for (Iterator<MetricFamilySamples> iterator = coll.iterator(); iterator.hasNext();) {
			MetricFamilySamples mfs = iterator.next();
			if (mfs.type == Type.UNKNOWN) {
				log.warn("Dropping metric {} from set of metrics, as it is untyped and the simpleclient's serialization coding does not properly support this", mfs.name);
				iterator.remove();
			}
		}
		
		return Collections.enumeration(coll);
	}
	
	public HashMap<String,MetricFamilySamples> getEnumerationMetricFamilySamplesInHashMap() {
		return new HashMap<>(this.map);
		// NB: This is not a deep clone, but only a shallow one!
	}
	
	public String toMetricsString() {
		Enumeration<MetricFamilySamples> resultEMFS = this.getEnumerationMetricFamilySamples();
		Writer writer = new StringWriter();
		try {
			TextFormat.writeFormat(TextFormat.CONTENT_TYPE_OPENMETRICS_100, writer, resultEMFS);
		} catch (IOException e) {
			log.error("IO Exception on StringWriter; uuuhhh...", e);
		}
		
		return writer.toString();
	}
	
}
