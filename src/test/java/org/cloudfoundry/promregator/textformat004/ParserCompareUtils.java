package org.cloudfoundry.promregator.textformat004;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;

import org.cloudfoundry.promregator.rewrite.MFSUtils;
import org.junit.jupiter.api.Assertions;

import io.prometheus.client.Collector.MetricFamilySamples;

public class ParserCompareUtils {
	public static void compareEMFS(Enumeration<MetricFamilySamples> expected, Enumeration<MetricFamilySamples> actual) {
		HashMap<String, MetricFamilySamples> expectedMap = MFSUtils.convertToEMFSToHashMap(expected);
		HashMap<String, MetricFamilySamples> actualMap = MFSUtils.convertToEMFSToHashMap(actual);
		
		Set<String> actualSet = actualMap.keySet();
		Set<String> expectedSet = expectedMap.keySet();
		
		Assertions.assertNotNull(actualSet);
		Assertions.assertNotNull(expectedSet);
		
		Assertions.assertEquals(expectedSet.size(), actualSet.size());
		Assertions.assertTrue(expectedSet.containsAll(actualSet));
		
		for (String metricName : expectedMap.keySet()) {
			MetricFamilySamples actualMFS = actualMap.get(metricName);
			MetricFamilySamples expectedMFS = expectedMap.get(metricName);
			
			Assertions.assertEquals(expectedMFS, actualMFS);
		}
	}
}
