package org.cloudfoundry.promregator.rewrite;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import io.prometheus.client.Collector.MetricFamilySamples;

public class MFSUtils {

    private MFSUtils() {}

	public static Map<String, MetricFamilySamples> convertToEMFSToHashMap(Enumeration<MetricFamilySamples> emfs) {
		Map<String, MetricFamilySamples> map = new HashMap<>();
		while (emfs.hasMoreElements()) {
			MetricFamilySamples mfs = emfs.nextElement();
			map.put(mfs.name, mfs);
		}
		return map;
	}
}
