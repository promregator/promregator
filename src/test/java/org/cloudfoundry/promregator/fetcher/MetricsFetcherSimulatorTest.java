package org.cloudfoundry.promregator.fetcher;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Child;

public class MetricsFetcherSimulatorTest {
	@AfterAll
	static public void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	public void testCall() throws Exception {
		Gauge up = Gauge.build("up_test", "help test").labelNames(new String[0]).create();
		Child upChild = up.labels(new String[0]);
		
		MetricsFetcherSimulator subject = new MetricsFetcherSimulator("accessUrl", 
				new NullEnricher(), 
				Mockito.mock(MetricsFetcherMetrics.class), upChild);
		
		FetchResult result = subject.call();
		
		Assertions.assertNotNull(result);
	}

}
