package org.cloudfoundry.promregator.rewrite;

import org.cloudfoundry.promregator.fetcher.FetchResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.exporter.common.TextFormat;

class MetricSetMergerTest {
	/*
	 * Warning! Be careful with """ notation here!
	 * The usage of \n and not \r\n is significant here!
	 */
	
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
	
	@Test
	void testAdditionalDataNull() {
		FetchResult fetchResult = new FetchResult("# TYPE test COUNTER\n"
				+ "test 2.0\n"
				+ "# EOF\n", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		
		MetricSetMerger subject = new MetricSetMerger(fetchResult, null);
		
		String actual = subject.merge();
		
		Assertions.assertEquals("""
				# TYPE test COUNTER
				test 2.0
				# EOF
				""", actual);
		
	}
	
	@Test
	void testAdditionalDataEmpty() {
		FetchResult fetchResult = new FetchResult("# TYPE test COUNTER\n"
				+ "test 2.0\n"
				+ "# EOF\n", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		
		MetricSetMerger subject = new MetricSetMerger(fetchResult, "");
		
		String actual = subject.merge();
		
		Assertions.assertEquals("""
				# TYPE test COUNTER
				test 2.0
				# EOF
				""", actual);
		
	}
	
	@Test
	void testFetchResultNull() {
		String additionalMetrics = "# TYPE test2 COUNTER\n"
				+ "test2 4.0\n";
		
		MetricSetMerger subject = new MetricSetMerger(null, additionalMetrics);
		
		String actual = subject.merge();
		
		Assertions.assertEquals("""
				# TYPE test2 COUNTER
				test2 4.0
				""", actual);
	}
	
	@Test
	void testOpenMetricsWithBothEOF() {
		final FetchResult fetchResult = new FetchResult("# TYPE test COUNTER\n"
				+ "test 2.0\n"
				+ "# EOF\n\r\n", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		
		final String additionalMetrics = "# TYPE test2 COUNTER\n"
				+ "test2 4.0\n"
				+ "# EOF\n\r\n";
		
		final MetricSetMerger subject = new MetricSetMerger(fetchResult, additionalMetrics);
		
		final String actual = subject.merge();
		
		final String expected = """
				# TYPE test COUNTER
				test 2.0
				# TYPE test2 COUNTER
				test2 4.0
				# EOF
				""";
		Assertions.assertEquals(expected, actual);
	}
}
