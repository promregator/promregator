package org.cloudfoundry.promregator.rewrite;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

public class MetricFamilySamplesEnricherTest {
	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	public void testSimple() {
		AbstractMetricFamilySamplesEnricher subject = new CFMetricFamilySamplesEnricher("testOrgName", "testSpaceName", "testComponent", "testInstance:42");

		List<Sample> samples = new LinkedList<>();
		Sample s = new Sample("dummyname", Arrays.asList(new String[] { "labelName" }), Arrays.asList(new String[] {"labelValue"}), 1.0);
		samples.add(s);
		
		MetricFamilySamples mfs = new MetricFamilySamples("dummyname", Type.GAUGE, "dummyHelp", samples);
		
		HashMap<String, MetricFamilySamples> map = new HashMap<>();
		map.put("metricName", mfs);
		
		HashMap<String, MetricFamilySamples> result = subject.determineEnumerationOfMetricFamilySamples(map);
		
		Assert.assertEquals(1, result.size());
		
		MetricFamilySamples testMFS = result.get("metricName");
		Assert.assertNotNull(testMFS);
		
		Assert.assertEquals(1, testMFS.samples.size());
		
		Sample testSample = testMFS.samples.get(0);
		Assert.assertNotNull(testSample);
		
		List<String> labelNamesList = testSample.labelNames;
		String[] labelNames = labelNamesList.toArray(new String[0]);
		
		Assert.assertEquals("labelName", labelNames[0]);
		Assert.assertEquals("org_name", labelNames[1]);
		Assert.assertEquals("space_name", labelNames[2]);
		Assert.assertEquals("app_name", labelNames[3]);
		Assert.assertEquals("cf_instance_id", labelNames[4]);
		Assert.assertEquals("cf_instance_number", labelNames[5]);
		
		List<String> labelValuesList = testSample.labelValues;
		String[] labelValues = labelValuesList.toArray(new String[0]);

		Assert.assertEquals("labelValue", labelValues[0]);
		Assert.assertEquals("testOrgName", labelValues[1]);
		Assert.assertEquals("testSpaceName", labelValues[2]);
		Assert.assertEquals("testComponent", labelValues[3]);
		Assert.assertEquals("testInstance:42", labelValues[4]);
		Assert.assertEquals("42", labelValues[5]);
	}

}
