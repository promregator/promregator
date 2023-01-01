package org.cloudfoundry.promregator.rewrite;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

class GenericMetricFamilySamplesPrefixRewriterTest {
	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	public void testPrefixesProperly() {
		GenericMetricFamilySamplesPrefixRewriter subject = new GenericMetricFamilySamplesPrefixRewriter("prefix");
		
		List<Sample> samples = new LinkedList<>();
		Sample s = new Sample("dummyname", Arrays.asList(new String[] { "labelName" }), Arrays.asList(new String[] {"labelValue"}), 1.0);
		samples.add(s);
		
		MetricFamilySamples mfs = new MetricFamilySamples("dummyname", Type.GAUGE, "dummyHelp", samples);
		
		HashMap<String, MetricFamilySamples> map = new HashMap<>();
		map.put("metricName", mfs);

		HashMap<String,MetricFamilySamples> result = subject.determineEnumerationOfMetricFamilySamples(map);
		
		MetricFamilySamples mfsResult = result.get("prefix_metricName");
		Assertions.assertNotNull(mfsResult);
		Assertions.assertEquals("prefix_dummyname", mfsResult.name);
		
		Assertions.assertEquals(1, mfsResult.samples.size());
		Sample sampleResult = mfsResult.samples.get(0);
		Assertions.assertEquals("prefix_dummyname", sampleResult.name);
	}
	
	@Test
	public void testDoesNotPrefixIfNotNeeded() {
		GenericMetricFamilySamplesPrefixRewriter subject = new GenericMetricFamilySamplesPrefixRewriter("prefix");
		
		List<Sample> samples = new LinkedList<>();
		Sample s = new Sample("prefix_dummyname", Arrays.asList(new String[] { "labelName" }), Arrays.asList(new String[] {"labelValue"}), 1.0);
		samples.add(s);
		
		MetricFamilySamples mfs = new MetricFamilySamples("prefix_dummyname", Type.GAUGE, "dummyHelp", samples);
		
		HashMap<String, MetricFamilySamples> map = new HashMap<>();
		map.put("prefix_metricName", mfs);

		HashMap<String,MetricFamilySamples> result = subject.determineEnumerationOfMetricFamilySamples(map);
		
		MetricFamilySamples mfsResult = result.get("prefix_metricName");
		Assertions.assertNotNull(mfsResult);
		Assertions.assertEquals("prefix_dummyname", mfsResult.name);
		
		Assertions.assertEquals(1, mfsResult.samples.size());
		Sample sampleResult = mfsResult.samples.get(0);
		Assertions.assertEquals("prefix_dummyname", sampleResult.name);
	}

}
