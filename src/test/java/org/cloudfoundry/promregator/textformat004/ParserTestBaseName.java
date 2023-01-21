package org.cloudfoundry.promregator.textformat004;

import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

public class ParserTestBaseName {

	@Test
	void testBaseNameUnchangedText004Counter() {
		String textToParse = "# TYPE basename counter\n" + 
				"basename 42\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		MetricFamilySamples mfs = resultMap.get("basename");
		Assertions.assertNotNull(mfs);
		
		Assertions.assertEquals(1, mfs.samples.size());
		Assertions.assertEquals("basename_total", mfs.samples.get(0).name);
		/*
		 * Warning! This was an incompatible change that was not made by us, but is
		 * enforced by simpleclient. See also https://github.com/prometheus/client_java/blob/parent-0.11.0/simpleclient/src/main/java/io/prometheus/client/Collector.java#L65
		 * This enforces that all samples of a counter-typed metric must always
		 * have the "_total" suffix at the end. There is no way around it 
		 * (even if we try to recreate the samples in the parser without that suffix)
		 */
	}
	
	@Test
	void testBaseNameUnchangedText004Gauge() {
		String textToParse = "# TYPE basename gauge\n" + 
				"basename 42\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		MetricFamilySamples mfs = resultMap.get("basename");
		Assertions.assertNotNull(mfs);
		
		Assertions.assertEquals(1, mfs.samples.size());
		Assertions.assertEquals("basename", mfs.samples.get(0).name);
	}
	
	@Test
	void testBaseNameUnchangedText004Unknown() {
		String textToParse = "basename 42\n";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		MetricFamilySamples mfs = resultMap.get("basename");
		Assertions.assertNotNull(mfs);
		
		Assertions.assertEquals(1, mfs.samples.size());
		Assertions.assertEquals("basename", mfs.samples.get(0).name);
	}
	
	@Test
	void testBaseNameUnchangedText004Histogram() {
		String textToParse = "# A histogram, which has a pretty complex representation in the text format:\n" + 
				"# HELP basename A histogram.\n" + 
				"# TYPE basename histogram\n" + 
				"basename{le=\"0.05\"} 24054\n" + 
				"basename{le=\"0.1\"} 33444\n" + 
				"basename_sum 53423\n" + 
				"basename_count 144320";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		MetricFamilySamples mfs = resultMap.get("basename");
		Assertions.assertNotNull(mfs);
		
		Assertions.assertEquals(4, mfs.samples.size());
		
		boolean base = false, sum = false, count = false;
		for (Sample sample : mfs.samples) {
			if (sample.name.equals("basename")) {
				base = true;
			} else if (sample.name.equals("basename_sum")) {
				sum = true;
			} else if (sample.name.equals("basename_count")) {
				count = true;
			} else {
				Assertions.fail("Unknown metricname in sample: "+sample);
			}
		}
		Assertions.assertTrue(base);
		Assertions.assertTrue(sum);
		Assertions.assertTrue(count);
	}
	
	@Test
	void testBaseNameUnchangedText004HistogramWithBucketSuffix() {
		String textToParse = "# A histogram, which has a pretty complex representation in the text format:\n" + 
				"# HELP basename A histogram.\n" + 
				"# TYPE basename histogram\n" + 
				"basename_bucket{le=\"0.05\"} 24054\n" + 
				"basename_bucket{le=\"0.1\"} 33444\n" + 
				"basename_sum 53423\n" + 
				"basename_count 144320";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		MetricFamilySamples mfs = resultMap.get("basename");
		Assertions.assertNotNull(mfs);
		
		Assertions.assertEquals(4, mfs.samples.size());
		
		boolean base = false, sum = false, count = false;
		for (Sample sample : mfs.samples) {
			if (sample.name.equals("basename_bucket")) {
				base = true;
			} else if (sample.name.equals("basename_sum")) {
				sum = true;
			} else if (sample.name.equals("basename_count")) {
				count = true;
			} else {
				Assertions.fail("Unknown metricname in sample: "+sample);
			}
		}
		Assertions.assertTrue(base);
		Assertions.assertTrue(sum);
		Assertions.assertTrue(count);
	}
	
	@Test
	void testBaseNameUnchangedText004Summary() {
		String textToParse = "# Finally a summary, which has a complex representation, too:\n" + 
				"# HELP basename A summary of the RPC duration in seconds.\n" + 
				"# TYPE basename summary\n" + 
				"basename{quantile=\"0.01\"} 3102\n" + 
				"basename{quantile=\"0.05\"} 3272\n" + 
				"basename_sum 1.7560473e+07\n" + 
				"basename_count 2693";
		
		Parser subject = new Parser(textToParse);
		HashMap<String, Collector.MetricFamilySamples> resultMap = subject.parse();
		
		Assertions.assertEquals(1, resultMap.keySet().size());
		MetricFamilySamples mfs = resultMap.get("basename");
		Assertions.assertNotNull(mfs);
		
		Assertions.assertEquals(4, mfs.samples.size());
		
		boolean base = false, sum = false, count = false;
		for (Sample sample : mfs.samples) {
			if (sample.name.equals("basename")) {
				base = true;
			} else if (sample.name.equals("basename_sum")) {
				sum = true;
			} else if (sample.name.equals("basename_count")) {
				count = true;
			} else {
				Assertions.fail("Unknown metricname in sample: "+sample);
			}
		}
		Assertions.assertTrue(base);
		Assertions.assertTrue(sum);
		Assertions.assertTrue(count);
	}
}

