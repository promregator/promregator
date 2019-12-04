package org.cloudfoundry.promregator.rewrite;

import java.util.Enumeration;
import java.util.HashMap;

import io.prometheus.client.Collector.MetricFamilySamples;

public class MFSUtils {

    private MFSUtils() {}

	public static HashMap<String, MetricFamilySamples> convertToEMFSToHashMap(Enumeration<MetricFamilySamples> emfs) {
		HashMap<String, MetricFamilySamples> map = new HashMap<>();
		while (emfs.hasMoreElements()) {
			MetricFamilySamples mfs = emfs.nextElement();
			map.put(mfs.name, mfs);
		}
		return map;
	}
}
