package org.cloudfoundry.promregator.lifecycle;

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.discovery.ConfigurationTargetInstance;
import org.cloudfoundry.promregator.discovery.Instance;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;

public class InstanceLifecycleHandlerTest {

	@AfterClass
	public static void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	public void testReceiverCleansUpProperly() {
		InstanceLifecycleHandler subject = new InstanceLifecycleHandler();
		
		ResolvedTarget rt = new ResolvedTarget();
		
		rt.setOrgName("testOrgName");
		rt.setSpaceName("testSpaceName");
		rt.setApplicationName("testapp");
		
		rt.setPath("/path/test");
		rt.setProtocol("https");
		
		Instance i = new ConfigurationTargetInstance(rt, "55820b2c-2fa5-11e8-b467-0ed5f89f718b:3", "access.url.bogus", null);
		
		/* generate some data first */
		
		String orgName = i.getOrgName();
		String spaceName = i.getSpaceName();
		String appName = i.getApplicationName();
		
		AbstractMetricFamilySamplesEnricher mfse = new CFAllLabelsMetricFamilySamplesEnricher(orgName, spaceName, appName, i.getInstanceId());
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, true);
		mfm.getFailedRequests().inc();
		mfm.getLatencyRequest().observe(42.0);
		mfm.getRequestSize().observe(2000);
		
		// trigger cleanup now
		subject.receiver(i.toInstanceKey());
		
		Enumeration<MetricFamilySamples> mfs = CollectorRegistry.defaultRegistry.metricFamilySamples();
		
		while(mfs.hasMoreElements()) {
			MetricFamilySamples metric = mfs.nextElement();
			
			for (MetricFamilySamples.Sample sample : metric.samples) {
				Assert.assertFalse(sample.labelValues.contains("testapp"));
			}
		}
		
	}

}
