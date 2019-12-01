package org.cloudfoundry.promregator.fetcher;

import java.util.LinkedList;
import java.util.Map;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Child;

public class MetricsFetcherSimulatorTest {
	@AfterClass
	public static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	public void testCall() throws Exception {
		AbstractMetricFamilySamplesEnricher mfse = new CFAllLabelsMetricFamilySamplesEnricher("testOrgName", "testSpaceName", "testapp", "testinstance1:0");
		
		Gauge up = Gauge.build("up_test", "help test").labelNames(CFAllLabelsMetricFamilySamplesEnricher.getEnrichingLabelNames()).create();
		Child upChild = up.labels(mfse.getEnrichedLabelValues(new LinkedList<>()).toArray(new String[0]));
		
		MetricsFetcherSimulator subject = new MetricsFetcherSimulator("accessUrl", 
				new NullEnricher(), mfse , 
				Mockito.mock(MetricsFetcherMetrics.class), upChild);
		
		Map<String, MetricFamilySamples> result = subject.call();
		
		Assert.assertEquals(3, result.size());
	}

}
