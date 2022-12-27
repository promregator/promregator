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
import org.cloudfoundry.promregator.textformat004.Parser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

class MergableMetricFamilySamplesTest {
	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	void testStraightFowardEnumeration() {
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
	void testStraightFowardHashMap() {
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		List<Sample> samples = new LinkedList<>();
		MetricFamilySamples mfs = new MetricFamilySamples("dummy", Type.COUNTER, "somehelp", samples);
		
		List<MetricFamilySamples> list = new LinkedList<>();
		list.add(mfs);
		
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
	void testUntypedMetricEnumeration() {
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		List<Sample> samples = new LinkedList<>();
		MetricFamilySamples mfs = new MetricFamilySamples("dummy", Type.UNKNOWN, "somehelp", samples);
		
		List<MetricFamilySamples> list = new LinkedList<>();
		list.add(mfs);
		
		HashMap<String, MetricFamilySamples> hmmfs = new HashMap<>();
		hmmfs.put("dummy", mfs);
		
		subject.merge(hmmfs);
		
		Enumeration<MetricFamilySamples> returnedEMFS = subject.getEnumerationMetricFamilySamples();
		
		Assertions.assertFalse(returnedEMFS.hasMoreElements());
	}

	@Test
	void testIssue104() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104-instance0.text004").toURI())));
		
		Parser source0 = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> map0 = source0.parse();
		Assertions.assertEquals(50, map0.size());
		
		
		textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104-instance1.text004").toURI())));
		
		Parser source1 = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> map1 = source1.parse();
		Assertions.assertEquals(50, map1.size());
		
		MergableMetricFamilySamples subject = new MergableMetricFamilySamples();
		
		subject.merge(map0);
		subject.merge(map1);
		
		String result = subject.toType004String();
		
		String expectedResult = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104-result.text004").toURI())));
		Assertions.assertEquals(expectedResult, result);
	}


}
