package org.cloudfoundry.promregator.rewrite;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

public class MergableMetricFamilySamplesTest {
	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	public void testStraightFowardEnumeration() {
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		List<Sample> samples = new LinkedList<>();
		MetricFamilySamples mfs = new MetricFamilySamples("dummy", Type.COUNTER, "somehelp", samples);
		
		List<MetricFamilySamples> list = new LinkedList<>();
		list.add(mfs);
		
		Enumeration<MetricFamilySamples> emfs = new Vector<MetricFamilySamples>(list).elements();
		
		subject.merge(emfs);
		
		Enumeration<MetricFamilySamples> returnedEMFS = subject.getEnumerationMetricFamilySamples();
		
		Assertions.assertTrue(returnedEMFS.hasMoreElements());
		MetricFamilySamples element = returnedEMFS.nextElement();
		Assertions.assertFalse(returnedEMFS.hasMoreElements());
		
		Assertions.assertEquals(mfs, element);
		
		HashMap<String, MetricFamilySamples> returnedHMMFS = subject.getEnumerationMetricFamilySamplesInHashMap();
		Assertions.assertEquals(1, returnedHMMFS.size());
		Assertions.assertEquals(mfs, returnedHMMFS.get("dummy"));
		
	}
	
	@Test
	public void testStraightFowardHashMap() {
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		List<Sample> samples = new LinkedList<>();
		MetricFamilySamples mfs = new MetricFamilySamples("dummy", Type.COUNTER, "somehelp", samples);
		
		HashMap<String, MetricFamilySamples> hmmfs = new HashMap<>();
		hmmfs.put("dummy", mfs);
		
		subject.merge(hmmfs);
		
		Enumeration<MetricFamilySamples> returnedEMFS = subject.getEnumerationMetricFamilySamples();
		
		Assertions.assertTrue(returnedEMFS.hasMoreElements());
		MetricFamilySamples element = returnedEMFS.nextElement();
		Assertions.assertFalse(returnedEMFS.hasMoreElements());
		
		Assertions.assertEquals(mfs, element);
		
		HashMap<String, MetricFamilySamples> returnedHMMFS = subject.getEnumerationMetricFamilySamplesInHashMap();
		Assertions.assertEquals(1, returnedHMMFS.size());
		Assertions.assertEquals(mfs, returnedHMMFS.get("dummy"));

	}
	
	@Test
	public void testUntypedMetricEnumeration() {
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		List<Sample> samples = new LinkedList<>();
		MetricFamilySamples mfs = new MetricFamilySamples("dummy", Type.UNKNOWN, "somehelp", samples);
		
		HashMap<String, MetricFamilySamples> hmmfs = new HashMap<>();
		hmmfs.put("dummy", mfs);
		
		subject.merge(hmmfs);
		
		Enumeration<MetricFamilySamples> returnedEMFS = subject.getEnumerationMetricFamilySamples();
		
		Assertions.assertFalse(returnedEMFS.hasMoreElements());
	}

}
