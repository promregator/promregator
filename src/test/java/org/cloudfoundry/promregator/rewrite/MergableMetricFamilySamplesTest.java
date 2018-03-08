package org.cloudfoundry.promregator.rewrite;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import org.junit.Assert;
import org.junit.Test;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

public class MergableMetricFamilySamplesTest {

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
		
		Assert.assertTrue(returnedEMFS.hasMoreElements());
		MetricFamilySamples element = returnedEMFS.nextElement();
		Assert.assertFalse(returnedEMFS.hasMoreElements());
		
		Assert.assertEquals(mfs, element);
		
		HashMap<String, MetricFamilySamples> returnedHMMFS = subject.getEnumerationMetricFamilySamplesInHashMap();
		Assert.assertEquals(1, returnedHMMFS.size());
		Assert.assertEquals(mfs, returnedHMMFS.get("dummy"));
		
	}
	
	@Test
	public void testStraightFowardHashMap() {
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		List<Sample> samples = new LinkedList<>();
		MetricFamilySamples mfs = new MetricFamilySamples("dummy", Type.COUNTER, "somehelp", samples);
		
		List<MetricFamilySamples> list = new LinkedList<>();
		list.add(mfs);
		
		HashMap<String, MetricFamilySamples> hmmfs = new HashMap<>();
		hmmfs.put("dummy", mfs);
		
		subject.merge(hmmfs);
		
		Enumeration<MetricFamilySamples> returnedEMFS = subject.getEnumerationMetricFamilySamples();
		
		Assert.assertTrue(returnedEMFS.hasMoreElements());
		MetricFamilySamples element = returnedEMFS.nextElement();
		Assert.assertFalse(returnedEMFS.hasMoreElements());
		
		Assert.assertEquals(mfs, element);
		
		HashMap<String, MetricFamilySamples> returnedHMMFS = subject.getEnumerationMetricFamilySamplesInHashMap();
		Assert.assertEquals(1, returnedHMMFS.size());
		Assert.assertEquals(mfs, returnedHMMFS.get("dummy"));

	}

}
