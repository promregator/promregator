package org.cloudfoundry.promregator.lifecycle;

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.messagebus.MessageBusTopic;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;

public class InstanceLifecycleHandlerTest {

	@AfterAll
	static public void cleanUp() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	public void testReceiverCleansUpProperly() {
		
		ResolvedTarget rt = new ResolvedTarget();
		
		rt.setOrgName("testOrgName");
		rt.setSpaceName("testSpaceName");
		rt.setApplicationName("testapp");
		
		rt.setPath("/path/test");
		rt.setProtocol("https");
		
		Instance i = new Instance(rt, "55820b2c-2fa5-11e8-b467-0ed5f89f718b:3", "access.url.bogus", false);
		
		/* generate some data first */
		
		String orgName = i.getTarget().getOrgName();
		String spaceName = i.getTarget().getSpaceName();
		String appName = i.getTarget().getApplicationName();
		
		AbstractMetricFamilySamplesEnricher mfse = new CFAllLabelsMetricFamilySamplesEnricher(orgName, spaceName, appName, i.getInstanceId());
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, true);
		mfm.getFailedRequests().inc();
		mfm.getLatencyRequest().observe(42.0);
		mfm.getRequestSize().observe(2000);
		
		InstanceLifecycleHandler subject = new InstanceLifecycleHandler();
		// trigger cleanup now
		subject.receiveMessage(MessageBusTopic.DISCOVERER_INSTANCE_REMOVED, i);
		
		Enumeration<MetricFamilySamples> mfs = CollectorRegistry.defaultRegistry.metricFamilySamples();
		
		while(mfs.hasMoreElements()) {
			MetricFamilySamples metric = mfs.nextElement();
			
			for (MetricFamilySamples.Sample sample : metric.samples) {
				Assertions.assertFalse(sample.labelValues.contains("testapp"));
			}
		}
		
	}

}
