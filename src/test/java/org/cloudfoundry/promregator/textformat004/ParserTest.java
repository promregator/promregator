package org.cloudfoundry.promregator.textformat004;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

public class ParserTest {
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testSimple() {
		String textToParse = "# Minimalistic line:\n" + 
				"metric_without_timestamp_and_labels 12.47\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());
		
		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_timestamp_and_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_timestamp_and_labels", Type.UNKNOWN, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		ParserCompareUtils.compareEMFS(expected, result);
	}

	@Test
	void testSimpleWithTimestampAndEmptyLine() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels 12.47 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNKNOWN, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testSimpleWithEFormat() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels 1.7560473e+07\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), 1.7560473e+07);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNKNOWN, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testSimplePlusInf() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels +Inf 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), Double.POSITIVE_INFINITY);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNKNOWN, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		ParserCompareUtils.compareEMFS(expected, result);
	}

	@Test
	void testSimpleMinusInf() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels -Inf 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		Sample sample = new Sample("metric_without_labels", new LinkedList<String>(), new LinkedList<String>(), Double.NEGATIVE_INFINITY);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_without_labels", Type.UNKNOWN, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		ParserCompareUtils.compareEMFS(expected, result);
	}

	@Test
	void testSimpleNaN() {
		String textToParse = "# Minimalistic line:\n" + 
				"\n"+
				"metric_without_labels NaN 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// compareEMFS does not properly work with NaN values
		// Thus, we have to check this explicitly here
		
		MetricFamilySamples mfs = result.nextElement();
		Assertions.assertFalse(result.hasMoreElements());
		
		Assertions.assertEquals("metric_without_labels", mfs.name);
		
		Assertions.assertEquals(1, mfs.samples.size());
		Sample actualSample = mfs.samples.get(0);
		Assertions.assertEquals("metric_without_labels", actualSample.name);
		Assertions.assertTrue(Double.isNaN(actualSample.value));
	}
	
	@Test
	void testGaugeWithTimestampAndEmptyLine() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels gauge\n" + 
				"\n"+
				"metric_without_labels 12.47 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testCounterWithTimestampAndEmptyLine() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels counter\n" + 
				"\n"+
				"metric_without_labels 12.47 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testHelp() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels counter\n" + 
				"# HELP metric_without_labels this is my help text\n" + 
				"metric_without_labels 12.47 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testHelpEscaping() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels counter\n" + 
				"# HELP metric_without_labels this is my help text with \\\\ backslashes escaped \\\\ and escaped newline \\n\n" + 
				"metric_without_labels 12.47 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testGaugeWithSingleLabel() {
		String textToParse = "# TYPE metric_with_label gauge\n" + 
				"metric_with_label{name=\"value\"} 12.47\n";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testGaugeWithMultipleLabels() {
		String textToParse = "# TYPE metric_with_label gauge\n" + 
				"metric_with_label{name=\"value\",second=\"somevalue\",third=\"next value\",} 12.47\n";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}

	@Test
	void testGaugeWithSingleLabelEscaping() {
		String textToParse = "# TYPE metric_with_label gauge\n" + 
				"metric_with_label{name=\"containing \\\" and \\\\ and \\n\"} 12.47\n";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testStandardExampleWithEscapingInLabels() {
		String textToParse = "# Escaping in label values:\n" + 
				"msdos_file_access_time_seconds{path=\"C:\\\\DIR\\\\FILE.TXT\",error=\"Cannot find file:\\n\\\"FILE.TXT\\\"\"} 1.458255915e9";
		
		Parser subject = new Parser(textToParse);
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
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("msdos_file_access_time_seconds", Type.UNKNOWN, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testHistogramSpecificationExample() {
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
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testHistogramSpecificationWithoutHelp() { // see also issue #73
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
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
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
	void testSummarySpecificationExample() {
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
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testSummarySpecificationWithoutHelp() { // see also issue #73
		String textToParse = "# Finally a summary, which has a complex representation, too:\n" + 
				"# TYPE rpc_duration_seconds summary\n" + 
				"rpc_duration_seconds{quantile=\"0.01\"} 3102\n" + 
				"rpc_duration_seconds{quantile=\"0.05\"} 3272\n" + 
				"rpc_duration_seconds{quantile=\"0.5\"} 4773\n" + 
				"rpc_duration_seconds{quantile=\"0.9\"} 9001\n" + 
				"rpc_duration_seconds{quantile=\"0.99\"} 76656\n" + 
				"rpc_duration_seconds_sum 1.7560473e+07\n" + 
				"rpc_duration_seconds_count 2693";
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
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
	void testHistogramWithLabels() {
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
		
		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
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
	void testSummaryWithLabel() {
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

		Parser subject = new Parser(textToParse);
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
		ParserCompareUtils.compareEMFS(expected, result);
	}

	@Test
	void testSimpleWithLabelIncludingBraces() {
		String textToParse = "# Finally a summary, which has a complex representation, too:\n" +
				"rpc_duration_seconds{name=\"val/{ue}\",quantile=\"0.01\",} 3102\n";

		Parser subject = new Parser(textToParse);
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

		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("rpc_duration_seconds", Type.UNKNOWN, "", samples);
		expectedList.add(expectedMFS);

		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);

		// compare
		ParserCompareUtils.compareEMFS(expected, result);
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
	void testVariant1() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("text004-variant1.txt").toURI())));
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		// ensure that all metrics are understood
		for (MetricFamilySamples mfs : resultMap.values()) {
			Assertions.assertNotEquals(Type.UNKNOWN, mfs.type);
		}
		
	}
	
	@Test
	void testLogbackWithMultipleLabelValues() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("text004-logback.txt").toURI())));
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		
		MetricFamilySamples mfs = resultMap.get("logback_events");
		Assertions.assertNotNull(mfs);
		
		// this file contains multiple samples for the same metric
		Assertions.assertEquals(5, mfs.samples.size());
	}
	
	@Test
	void testIssue104() throws IOException, URISyntaxException {
		String textToParse = new String(Files.readAllBytes(Paths.get(getClass().getResource("issue104.txt").toURI())));
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		
		MetricFamilySamples mfs = resultMap.get("http_server_requests_seconds");
		Assertions.assertNotNull(mfs);
		
		// this file contains multiple samples for the same metric
		Assertions.assertEquals(66, mfs.samples.size());
		
		Assertions.assertEquals(0.034350549,  mfs.samples.get(65).value, 0.001);
	}
	
	@Test
	// see also issue #175
	void testGaugeNonParsableJunk() {
		String textToParse = "# TYPE metric_with_label gauge\n" + 
				"metric_with_label{name=\"xyz\"} 12.47\n"
				+ "\n"
				+ "some_garbage abc----\n"
				+ "\n"
				+ "# TYPE another_metric gauge\n"
				+ "another_metric 123.1\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		// creating expected result
		LinkedList<Collector.MetricFamilySamples> expectedList = new LinkedList<>();

		List<Sample> samples = new LinkedList<>();
		
		List<String> labelNames = new LinkedList<>();
		labelNames.add("name");
		List<String> labelValues = new LinkedList<>();
		labelValues.add("xyz");
		
		Sample sample = new Sample("metric_with_label", labelNames, labelValues, 12.47);
		samples.add(sample);
		
		Collector.MetricFamilySamples expectedMFS = new Collector.MetricFamilySamples("metric_with_label", Type.GAUGE, "", samples);
		expectedList.add(expectedMFS);

		samples = new LinkedList<>();

		samples.add(new Sample("another_metric", Collections.emptyList(), Collections.emptyList(), 123.1));
		expectedMFS = new Collector.MetricFamilySamples("another_metric", Type.GAUGE, "", samples);
		expectedList.add(expectedMFS);
		
		Enumeration<Collector.MetricFamilySamples> expected = Collections.enumeration(expectedList);
		
		// compare
		ParserCompareUtils.compareEMFS(expected, result);
	}
	
	@Test
	void testCounterWithTotalSuffix() {
		String textToParse = "# Simple metric without labels:\n" + 
				"# TYPE metric_without_labels_total counter\n" + 
				"# HELP metric_without_labels_total this is my help text\n" + 
				"metric_without_labels_total 12.47 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		ArrayList<MetricFamilySamples> resultList = Collections.list(result);
		
		Assertions.assertEquals(1, resultList.size());
		MetricFamilySamples mfs = resultList.get(0);
		
		Assertions.assertEquals("metric_without_labels", mfs.name);
		Assertions.assertEquals(Type.COUNTER, mfs.type);
		Assertions.assertEquals("this is my help text", mfs.help);
		
		Assertions.assertEquals(1, mfs.samples.size());
		Sample sampleResult = mfs.samples.get(0);
		
		Assertions.assertEquals("metric_without_labels_total", sampleResult.name);
	}
	
	@Test
	void testCounterWithInfoSuffix() {
		/* Live example of an info metric:
		 * 
		 * build_info{commit_id="42806c2f7f1e17a63d94db9d561d220f53d38ee0",commit_time="2019-11-16 13:53:46Z",build_time="2019-11-16 13:57:20Z",branch="v19.57.x",version="19.57.3",} 1.0
		 */
		
		String textToParse = "# Simple metric without labels:\n" + 
				"# HELP metric_without_labels_info this is my help text\n" + 
				"# TYPE metric_without_labels_info info\n" + 
				"metric_without_labels_info 12.47 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		ArrayList<MetricFamilySamples> resultList = Collections.list(result);
		
		Assertions.assertEquals(1, resultList.size());
		MetricFamilySamples mfs = resultList.get(0);
		
		Assertions.assertEquals("metric_without_labels", mfs.name);
		Assertions.assertEquals(Type.INFO, mfs.type);
		Assertions.assertEquals("this is my help text", mfs.help);
		
		Assertions.assertEquals(1, mfs.samples.size());
		Sample sampleResult = mfs.samples.get(0);
		
		Assertions.assertEquals("metric_without_labels_info", sampleResult.name);
	}
	
	@Test
	void testStateset() {
		/* see also https://github.com/OpenObservability/OpenMetrics/blob/111feb202360b8650092f7de15a600e34a4ce0ba/specification/OpenMetrics.md#stateset
		 * 
		 * metric{label="1234"} 1.0
		 */
		
		String textToParse = 
				"# HELP metric_with_a_label this is my help text\n" + 
				"# TYPE metric_with_a_label stateset\n" + 
				"metric_with_a_label{dimension=\"abc\"} 1 123456789012345600\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		Enumeration<Collector.MetricFamilySamples> result = Collections.enumeration(resultMap.values());

		ArrayList<MetricFamilySamples> resultList = Collections.list(result);
		
		Assertions.assertEquals(1, resultList.size());
		MetricFamilySamples mfs = resultList.get(0);
		
		Assertions.assertEquals("metric_with_a_label", mfs.name);
		Assertions.assertEquals(Type.STATE_SET, mfs.type);
		Assertions.assertEquals("this is my help text", mfs.help);
		
		Assertions.assertEquals(1, mfs.samples.size());
		Sample sampleResult = mfs.samples.get(0);
		
		Assertions.assertEquals("metric_with_a_label", sampleResult.name);
	}

}
