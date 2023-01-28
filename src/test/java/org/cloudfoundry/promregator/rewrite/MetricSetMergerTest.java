package org.cloudfoundry.promregator.rewrite;

import org.cloudfoundry.promregator.fetcher.FetchResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.exporter.common.TextFormat;

class MetricSetMergerTest {

	@Test
	void testTextFormat004() {
		FetchResult fetchResult = new FetchResult("# TYPE test COUNTER\n"
				+ "test 2.0\n", TextFormat.CONTENT_TYPE_004);
		
		String additionalMetrics = "# TYPE test2 COUNTER\n"
				+ "test2 4.0\n";
		
		MetricSetMerger subject = new MetricSetMerger(fetchResult, additionalMetrics);
		
		String actual = subject.merge();
		
		Assertions.assertEquals("""
				# TYPE test COUNTER
				test 2.0
				# TYPE test2 COUNTER
				test2 4.0
				""", actual);
	}
	
	@Test
	void testOpenMetricsWithEOF() {
		FetchResult fetchResult = new FetchResult("# TYPE test COUNTER\n"
				+ "test 2.0\n"
				+ "# EOF\n", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		
		String additionalMetrics = "# TYPE test2 COUNTER\n"
				+ "test2 4.0\n";
		
		MetricSetMerger subject = new MetricSetMerger(fetchResult, additionalMetrics);
		
		String actual = subject.merge();
		
		Assertions.assertEquals("""
				# TYPE test COUNTER
				test 2.0
				# TYPE test2 COUNTER
				test2 4.0
				# EOF
				""", actual);
	}
	
	@Test
	void testOpenMetricsWithEOFAdditionalNewLines() {
		FetchResult fetchResult = new FetchResult("# TYPE test COUNTER\n"
				+ "test 2.0\n"
				+ "# EOF\n\r\n", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		
		String additionalMetrics = "# TYPE test2 COUNTER\n"
				+ "test2 4.0\n";
		
		MetricSetMerger subject = new MetricSetMerger(fetchResult, additionalMetrics);
		
		String actual = subject.merge();
		
		Assertions.assertEquals("""
				# TYPE test COUNTER
				test 2.0
				# TYPE test2 COUNTER
				test2 4.0
				# EOF
				""", actual);
	}

	@Test
	void testOpenMetricsWithoutEOF() {
		FetchResult fetchResult = new FetchResult("# TYPE test COUNTER\n"
				+ "test 2.0\n", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		
		String additionalMetrics = "# TYPE test2 COUNTER\n"
				+ "test2 4.0\n";
		
		MetricSetMerger subject = new MetricSetMerger(fetchResult, additionalMetrics);
		
		String actual = subject.merge();
		
		Assertions.assertEquals("""
				# TYPE test COUNTER
				test 2.0
				# TYPE test2 COUNTER
				test2 4.0
				# EOF
				""", actual);
		
	}
}
