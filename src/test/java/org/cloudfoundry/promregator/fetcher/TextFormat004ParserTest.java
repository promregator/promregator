package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.rewrite.MFSUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

public class TextFormat004ParserTest {
	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	public static void compareEMFS(Enumeration<MetricFamilySamples> expected, Enumeration<MetricFamilySamples> actual) {
		HashMap<String, MetricFamilySamples> expectedMap = MFSUtils.convertToEMFSToHashMap(expected);
		HashMap<String, MetricFamilySamples> actualMap = MFSUtils.convertToEMFSToHashMap(actual);
		
		Assert.assertTrue(EqualsBuilder.reflectionEquals(actualMap.keySet(), expectedMap.keySet(), false));
		
		for (String metricName : expectedMap.keySet()) {
			MetricFamilySamples actualMFS = actualMap.get(metricName);
			MetricFamilySamples expectedMFS = expectedMap.get(metricName);
			
			Assert.assertTrue(EqualsBuilder.reflectionEquals(actualMFS, expectedMFS));
		}
	}
	
	@Test
	public void testSimple() {
		String textToParse = "# Minimalistic line:\n" + 
				"metric_without_timestamp_and_labels 12.47\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());
		
		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_timestamp_and_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_timestamp_and_labels", Type.UNTYPED, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}

	@Test
	public void testSimpleWithTimestampAndEmptyLine() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels 12.47 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNTYPED, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testSimpleWithEFormat() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels 1.7560473e+07\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 1.7560473e+07);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNTYPED, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testSimplePlusInf() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels +Inf 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), Double.POSITIVE_INFINITY);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNTYPED, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}

	@Test
	public void testSimpleMinusInf() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels -Inf 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), Double.NEGATIVE_INFINITY);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNTYPED, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}

	@Test
	public void testSimpleNaN() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels NaN 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// compareEMFS does not properly work with NaN values
		// Thus, we have to check this explicitly here
		
		MetricFamilySamples mfs = result.nextElement();
		Assert.assertFalse(result.hasMoreElements());
		
		Assert.assertEquals("metric_without_labels", mfs.name);
		
		Assert.assertEquals(1, mfs.samples.size());
		Sample actualSample = mfs.samples.get(0);
		Assert.assertEquals("metric_without_labels", actualSample.name);
		Assert.assertTrue(Double.isNaN(actualSample.value));
	}
	
	@Test
	public void testSimpleNan() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels Nan 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// compareEMFS does not properly work with NaN values
		// Thus, we have to check this explicitly here
		
		MetricFamilySamples mfs = result.nextElement();
		Assert.assertFalse(result.hasMoreElements());
		
		Assert.assertEquals("metric_without_labels", mfs.name);
		
		Assert.assertEquals(1, mfs.samples.size());
		Sample actualSample = mfs.samples.get(0);
		Assert.assertEquals("metric_without_labels", actualSample.name);
		Assert.assertTrue(Double.isNaN(actualSample.value));
	}
	
	@Test
	public void testGaugeWithTimestampAndEmptyLine() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels gauge\n" + 
				"\n"+
				"metric_without_labels 12.47 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.GAUGE, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testCounterWithTimestampAndEmptyLine() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels counter\n" + 
				"\n"+
				"metric_without_labels 12.47 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.COUNTER, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testHelp() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels counter\n" + 
				"# HELP metric_without_labels this is my help text\n" + 
				"metric_without_labels 12.47 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.COUNTER, "this is my help text", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testHelpEscaping() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels counter\n" + 
				"# HELP metric_without_labels this is my help text with \\\\ backslashes escaped \\\\ and escaped newline \\n\n" + 
				"metric_without_labels 12.47 123456789012345600\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.COUNTER, "this is my help text with \\ backslashes escaped \\ and escaped newline \n", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testGaugeWithSingleLabel() {
		String textToParse = "# TYPE metric_with_label gauge\n" + 
				"metric_with_label{name=\"value\"} 12.47\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		List<String> labelNames = new LinkedList<>();
		labelNames.add("name");
		List<String> labelValues = new LinkedList<>();
		labelValues.add("value");
		
		Sample sample = new Sample("metric_with_label", labelNames, labelValues, 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_with_label", Type.GAUGE, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testGaugeWithMultipleLabels() {
		String textToParse = "# TYPE metric_with_label gauge\n" + 
				"metric_with_label{name=\"value\",second=\"somevalue\",third=\"next value\",} 12.47\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		List<String> labelNames = new LinkedList<>();
		labelNames.add("name");
		labelNames.add("second");
		labelNames.add("third");
		List<String> labelValues = new LinkedList<>();
		labelValues.add("value");
		labelValues.add("somevalue");
		labelValues.add("next value");
		
		Sample sample = new Sample("metric_with_label", labelNames, labelValues, 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_with_label", Type.GAUGE, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}

	@Test
	public void testGaugeWithSingleLabelEscaping() {
		String textToParse = "# TYPE metric_with_label gauge\n" + 
				"metric_with_label{name=\"containing \\\" and \\\\ and \\n\"} 12.47\n";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		List<String> labelNames = new LinkedList<>();
		labelNames.add("name");
		List<String> labelValues = new LinkedList<>();
		labelValues.add("containing \" and \\ and \n");
		
		Sample sample = new Sample("metric_with_label", labelNames, labelValues, 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_with_label", Type.GAUGE, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testStandardExampleWithEscapingInLabels() {
		String textToParse = "# Escaping in label values:\n" + 
				"msdos_file_access_time_seconds{path=\"C:\\\\DIR\\\\FILE.TXT\",error=\"Cannot find file:\\n\\\"FILE.TXT\\\"\"} 1.458255915e9";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		List<String> labelNames = new LinkedList<>();
		labelNames.add("path");
		labelNames.add("error");
		List<String> labelValues = new LinkedList<>();
		labelValues.add("C:\\DIR\\FILE.TXT");
		labelValues.add("Cannot find file:\n\"FILE.TXT\"");
		
		Sample sample = new Sample("msdos_file_access_time_seconds", labelNames, labelValues, 1.458255915e9);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("msdos_file_access_time_seconds", Type.UNTYPED, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testHistogramSpecificationExample() {
		String textToParse = "# A histogram, which has a pretty complex representation in the text format:\n" + 
				"# HELP http_request_duration_seconds A histogram of the request duration.\n" + 
				"# TYPE http_request_duration_seconds histogram\n" + 
				"http_request_duration_seconds_bucket{le=\"0.05\"} 24054\n" + 
				"http_request_duration_seconds_bucket{le=\"0.1\"} 33444\n" + 
				"http_request_duration_seconds_bucket{le=\"0.2\"} 100392\n" + 
				"http_request_duration_seconds_bucket{le=\"0.5\"} 129389\n" + 
				"http_request_duration_seconds_bucket{le=\"1\"} 133988\n" + 
				"http_request_duration_seconds_bucket{le=\"+Inf\"} 144320\n" + 
				"http_request_duration_seconds_sum 53423\n" + 
				"http_request_duration_seconds_count 144320";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		Sample sample = null;
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.05", 24054); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.1", 33444); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.2", 100392); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.5", 129389); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "1", 133988); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "+Inf", 144320); samples.add(sample);
		sample = new Sample("http_request_duration_seconds_sum", new LinkedList<>(), new LinkedList<>(), 53423); samples.add(sample);
		sample = new Sample("http_request_duration_seconds_count", new LinkedList<>(), new LinkedList<>(), 144320); samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("http_request_duration_seconds", Type.HISTOGRAM, "A histogram of the request duration.", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testHistogramSpecificationWithoutHelp() { // see also issue #73
		String textToParse = "# A histogram, which has a pretty complex representation in the text format:\n" + 
				"# TYPE http_request_duration_seconds histogram\n" + 
				"http_request_duration_seconds_bucket{le=\"0.05\"} 24054\n" + 
				"http_request_duration_seconds_bucket{le=\"0.1\"} 33444\n" + 
				"http_request_duration_seconds_bucket{le=\"0.2\"} 100392\n" + 
				"http_request_duration_seconds_bucket{le=\"0.5\"} 129389\n" + 
				"http_request_duration_seconds_bucket{le=\"1\"} 133988\n" + 
				"http_request_duration_seconds_bucket{le=\"+Inf\"} 144320\n" + 
				"http_request_duration_seconds_sum 53423\n" + 
				"http_request_duration_seconds_count 144320";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		Sample sample = null;
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.05", 24054); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.1", 33444); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.2", 100392); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "0.5", 129389); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "1", 133988); samples.add(sample);
		sample = createSampleForHistogram("http_request_duration_seconds_bucket", "+Inf", 144320); samples.add(sample);
		sample = new Sample("http_request_duration_seconds_sum", new LinkedList<>(), new LinkedList<>(), 53423); samples.add(sample);
		sample = new Sample("http_request_duration_seconds_count", new LinkedList<>(), new LinkedList<>(), 144320); samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("http_request_duration_seconds", Type.HISTOGRAM, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	private static Sample createSampleForHistogram(String bucketMetricName, String leValue, double value) {
		List<String> labelNames = new LinkedList<>();
		labelNames.add("le");
		List<String> labelValues = new LinkedList<>();
		labelValues.add(leValue);
		
		Sample sample = new Sample(bucketMetricName, labelNames, labelValues, value); 
		return sample;
	}
	

	@Test
	public void testSummarySpecificationExample() {
		String textToParse = "# Finally a summary, which has a complex representation, too:\n" + 
				"# HELP rpc_duration_seconds A summary of the RPC duration in seconds.\n" + 
				"# TYPE rpc_duration_seconds summary\n" + 
				"rpc_duration_seconds{quantile=\"0.01\"} 3102\n" + 
				"rpc_duration_seconds{quantile=\"0.05\"} 3272\n" + 
				"rpc_duration_seconds{quantile=\"0.5\"} 4773\n" + 
				"rpc_duration_seconds{quantile=\"0.9\"} 9001\n" + 
				"rpc_duration_seconds{quantile=\"0.99\"} 76656\n" + 
				"rpc_duration_seconds_sum 1.7560473e+07\n" + 
				"rpc_duration_seconds_count 2693";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		Sample sample = null;
		sample = createSampleForSummary("rpc_duration_seconds", "0.01", 3102); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.05", 3272); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.5", 4773); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.9", 9001); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.99", 76656); samples.add(sample);
		sample = new Sample("rpc_duration_seconds_sum", new LinkedList<>(), new LinkedList<>(), 1.7560473e+07); samples.add(sample);
		sample = new Sample("rpc_duration_seconds_count", new LinkedList<>(), new LinkedList<>(), 2693); samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("rpc_duration_seconds", Type.SUMMARY, "A summary of the RPC duration in seconds.", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	@Test
	public void testSummarySpecificationWithoutHelp() { // see also issue #73
		String textToParse = "# Finally a summary, which has a complex representation, too:\n" + 
				"# TYPE rpc_duration_seconds summary\n" + 
				"rpc_duration_seconds{quantile=\"0.01\"} 3102\n" + 
				"rpc_duration_seconds{quantile=\"0.05\"} 3272\n" + 
				"rpc_duration_seconds{quantile=\"0.5\"} 4773\n" + 
				"rpc_duration_seconds{quantile=\"0.9\"} 9001\n" + 
				"rpc_duration_seconds{quantile=\"0.99\"} 76656\n" + 
				"rpc_duration_seconds_sum 1.7560473e+07\n" + 
				"rpc_duration_seconds_count 2693";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		Sample sample = null;
		sample = createSampleForSummary("rpc_duration_seconds", "0.01", 3102); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.05", 3272); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.5", 4773); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.9", 9001); samples.add(sample);
		sample = createSampleForSummary("rpc_duration_seconds", "0.99", 76656); samples.add(sample);
		sample = new Sample("rpc_duration_seconds_sum", new LinkedList<>(), new LinkedList<>(), 1.7560473e+07); samples.add(sample);
		sample = new Sample("rpc_duration_seconds_count", new LinkedList<>(), new LinkedList<>(), 2693); samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("rpc_duration_seconds", Type.SUMMARY, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	private static Sample createSampleForSummary(String bucketMetricName, String quantileValue, double value) {
		List<String> labelNames = new LinkedList<>();
		labelNames.add("quantile");
		List<String> labelValues = new LinkedList<>();
		labelValues.add(quantileValue);
		
		Sample sample = new Sample(bucketMetricName, labelNames, labelValues, value); 
		return sample;
	}
	
	@Test
	public void testHistogramWithLabels() {
		String textToParse = "# A histogram, which has a pretty complex representation in the text format:\n" + 
				"# HELP http_request_duration_seconds A histogram of the request duration.\n" + 
				"# TYPE http_request_duration_seconds histogram\n" + 
				"http_request_duration_seconds_bucket{name=\"value\",le=\"0.05\",} 24054\n" + 
				"http_request_duration_seconds_bucket{name=\"value\",le=\"0.1\"} 33444\n" + 
				"http_request_duration_seconds_bucket{le=\"0.2\",name=\"value\",} 100392\n" + 
				"http_request_duration_seconds_bucket{le=\"0.5\",name=\"value\"} 129389\n" + 
				"http_request_duration_seconds_bucket{name=\"value\",le=\"1\"} 133988\n" + 
				"http_request_duration_seconds_bucket{name=\"value\",le=\"+Inf\"} 144320\n" + 
				"http_request_duration_seconds_sum 53423\n" + 
				"http_request_duration_seconds_count 144320";
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		Sample sample = null;
		sample = createSampleForHistogramWithDummyLabel("http_request_duration_seconds_bucket", "0.05", 24054, true); samples.add(sample);
		sample = createSampleForHistogramWithDummyLabel("http_request_duration_seconds_bucket", "0.1", 33444, true); samples.add(sample);
		sample = createSampleForHistogramWithDummyLabel("http_request_duration_seconds_bucket", "0.2", 100392, false); samples.add(sample);
		sample = createSampleForHistogramWithDummyLabel("http_request_duration_seconds_bucket", "0.5", 129389, false); samples.add(sample);
		sample = createSampleForHistogramWithDummyLabel("http_request_duration_seconds_bucket", "1", 133988, true); samples.add(sample);
		sample = createSampleForHistogramWithDummyLabel("http_request_duration_seconds_bucket", "+Inf", 144320, true); samples.add(sample);
		sample = new Sample("http_request_duration_seconds_sum", new LinkedList<>(), new LinkedList<>(), 53423); samples.add(sample);
		sample = new Sample("http_request_duration_seconds_count", new LinkedList<>(), new LinkedList<>(), 144320); samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("http_request_duration_seconds", Type.HISTOGRAM, "A histogram of the request duration.", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		compareEMFS(expected, result);
	}
	
	private static Sample createSampleForHistogramWithDummyLabel(String bucketMetricName, String leValue, double value, boolean firstPosition) {
		List<String> labelNames = new LinkedList<>();
		if (firstPosition) {
			labelNames.add("name");
		}
		labelNames.add("le");
		if (!firstPosition) {
			labelNames.add("name");
		}
		
		List<String> labelValues = new LinkedList<>();
		if (firstPosition) {
			labelValues.add("value");
		}
		labelValues.add(leValue);
		if (!firstPosition) {
			labelValues.add("value");
		}
		
		Sample sample = new Sample(bucketMetricName, labelNames, labelValues, value); 
		return sample;
	}

	@Test
	public void testSummaryWithLabel() {
		String textToParse = "# Finally a summary, which has a complex representation, too:\n" +
				"# HELP rpc_duration_seconds A summary of the RPC duration in seconds.\n" +
				"# TYPE rpc_duration_seconds summary\n" +
				"rpc_duration_seconds{name=\"value\",quantile=\"0.01\",} 3102\n" +
				"rpc_duration_seconds{name=\"value\",quantile=\"0.05\"} 3272\n" +
				"rpc_duration_seconds{quantile=\"0.5\",name=\"value\",} 4773\n" +
				"rpc_duration_seconds{quantile=\"0.9\",name=\"value\"} 9001\n" +
				"rpc_duration_seconds{name=\"value\",quantile=\"0.99\"} 76656\n" +
				"rpc_duration_seconds_sum 1.7560473e+07\n" +
				"rpc_duration_seconds_count 2693";

		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();

		Sample sample = null;
		sample = createSampleForSummaryWithDummyLabel("rpc_duration_seconds", "0.01", 3102, true); samples.add(sample);
		sample = createSampleForSummaryWithDummyLabel("rpc_duration_seconds", "0.05", 3272, true); samples.add(sample);
		sample = createSampleForSummaryWithDummyLabel("rpc_duration_seconds", "0.5", 4773, false); samples.add(sample);
		sample = createSampleForSummaryWithDummyLabel("rpc_duration_seconds", "0.9", 9001, false); samples.add(sample);
		sample = createSampleForSummaryWithDummyLabel("rpc_duration_seconds", "0.99", 76656, true); samples.add(sample);
		sample = new Sample("rpc_duration_seconds_sum", new LinkedList<>(), new LinkedList<>(), 1.7560473e+07); samples.add(sample);
		sample = new Sample("rpc_duration_seconds_count", new LinkedList<>(), new LinkedList<>(), 2693); samples.add(sample);

		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("rpc_duration_seconds", Type.SUMMARY, "A summary of the RPC duration in seconds.", samples);
		expectedList.add(expectedMFS);

		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);

		// compare
		compareEMFS(expected, result);
	}

	@Test
	public void testSimpleWithLabelIncludingBraces() {
		String textToParse = "# Finally a summary, which has a complex representation, too:\n" +
				"rpc_duration_seconds{name=\"val/{ue}\",quantile=\"0.01\",} 3102\n";

		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();

		List<String> labelNames = new LinkedList<>();
		labelNames.add("name");
		labelNames.add("quantile");
		List<String> labelValues = new LinkedList<>();
		labelValues.add("val/{ue}");
		labelValues.add("0.01");
		Sample sample = new Sample("rpc_duration_seconds", labelNames, labelValues, 3102);
		samples.add(sample);

		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("rpc_duration_seconds", Type.UNTYPED, "", samples);
		expectedList.add(expectedMFS);

		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);

		// compare
		compareEMFS(expected, result);
	}

	private static Sample createSampleForSummaryWithDummyLabel(String bucketMetricName, String quantileValue, double value, boolean firstPosition) {
		List<String> labelNames = new LinkedList<>();
		if (firstPosition) {
			labelNames.add("name");
		}
		labelNames.add("quantile");
		if (!firstPosition) {
			labelNames.add("name");
		}
		List<String> labelValues = new LinkedList<>();
		if (firstPosition) {
			labelValues.add("value");
		}
		labelValues.add(quantileValue);
		if (!firstPosition) {
			labelValues.add("value");
		}
		
		Sample sample = new Sample(bucketMetricName, labelNames, labelValues, value); 
		return sample;
	}

	@Test
	public void testVariant1() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("text004-variant1.txt").toURI())));
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		// ensure that all metrics are understood
		for (MetricFamilySamples mfs : resultMap.values()) {
			Assert.assertNotEquals(Type.UNTYPED, mfs.type);
		}
		
	}
	
	@Test
	public void testLogbackWithMultipleLabelValues() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("text004-logback.txt").toURI())));
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assert.assertEquals(1, resultMap.keySet().size());
		
		MetricFamilySamples mfs = resultMap.get("logback_events_total");
		Assert.assertNotNull(mfs);
		
		// this file contains multiple samples for the same metric
		Assert.assertEquals(5, mfs.samples.size());
	}
	
	@Test
	public void testIssue104() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104.txt").toURI())));
		
		TextFormat004Parser subject = new TextFormat004Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assert.assertEquals(1, resultMap.keySet().size());
		
		MetricFamilySamples mfs = resultMap.get("http_server_requests_seconds");
		Assert.assertNotNull(mfs);
		
		// this file contains multiple samples for the same metric
		Assert.assertEquals(66, mfs.samples.size());
		
		Assert.assertEquals(0.034350549,  mfs.samples.get(65).value, 0.001);
	}
		
}
