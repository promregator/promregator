package org.cloudfoundry.promregator.rewrite;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.fetcher.TextFormat004Parser;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

public class MergableMetricFamilySamplesTest {
	@AfterClass
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
	
	@Test
	public void testUntypedMetricEnumeration() {
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		List<Sample> samples = new LinkedList<>();
		MetricFamilySamples mfs = new MetricFamilySamples("dummy", Type.UNTYPED, "somehelp", samples);
		
		List<MetricFamilySamples> list = new LinkedList<>();
		list.add(mfs);
		
		HashMap<String, MetricFamilySamples> hmmfs = new HashMap<>();
		hmmfs.put("dummy", mfs);
		
		subject.merge(hmmfs);
		
		Enumeration<MetricFamilySamples> returnedEMFS = subject.getEnumerationMetricFamilySamples();
		
		Assert.assertFalse(returnedEMFS.hasMoreElements());
	}

	@Test
	public void testIssue104() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104-instance0.text004").toURI())));
		
		TextFormat004Parser source0 = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> map0 = source0.parse();
		Assert.assertEquals(50, map0.size());
		
		
		textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104-instance1.text004").toURI())));
		
		TextFormat004Parser source1 = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> map1 = source1.parse();
		Assert.assertEquals(50, map1.size());
		
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		subject.merge(map0);
		subject.merge(map1);
		
		String result = subject.toType004String();
		
		String expectedResult = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104-result.text004").toURI())));
		Assert.assertEquals(expectedResult, result);
	}


}
