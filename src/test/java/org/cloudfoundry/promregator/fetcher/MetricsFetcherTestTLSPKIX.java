package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.mockServer.MetricsEndpointMockServerTLS;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.prometheus.client.Collector.MetricFamilySamples;

public class MetricsFetcherTestTLSPKIX {

	private static final String DUMMY_METRICS_LIST = "# HELP dummy This is a dummy metric\n"+
			"# TYPE dummy counter\n"+
			"dummy 42 1395066363000";
	private MetricsEndpointMockServerTLS mems;
	
	public MetricsFetcherTestTLSPKIX() {
	}
	
	@BeforeEach
	void startUpMetricsEndpointServer() throws IOException {
		this.mems = new MetricsEndpointMockServerTLS();
		this.mems.start();
	}
	
	@AfterEach
	void tearDownMetricsEndpointServer() {
		this.mems.stop();
	}

	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	private static class NullMetricFamilySamplesEnricher extends CFAllLabelsMetricFamilySamplesEnricher {

		public NullMetricFamilySamplesEnricher(String orgName, String spaceName, String appName, String instance) {
			super(orgName, spaceName, appName, instance);
		}

		@Override
		public HashMap<String, MetricFamilySamples> determineEnumerationOfMetricFamilySamples(HashMap<String, MetricFamilySamples> mfs) {
			return mfs;
		}
	}
	
	@Test
	void testPKIXErrorOnSelfSignedCertificateInDefaultMode() throws Exception {
		String instanceId = "abcd:4";
		NullMetricFamilySamplesEnricher dummymfse = new NullMetricFamilySamplesEnricher("dummy", "dummy", "dummy", "dummy:0");
		List<String> labelValues = dummymfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, false);
		UUID currentUUID = UUID.randomUUID();
		
		CFMetricsFetcherConfig config = new CFMetricsFetcherConfig();
		config.setMetricsFetcherMetrics(mfm);
		config.setPromregatorInstanceIdentifier(currentUUID);
		config.setConnectionTimeoutInMillis(5000);
		config.setSocketReadTimeoutInMillis(5000);
		
		CFMetricsFetcher subject = new CFMetricsFetcher("https://localhost:9003/metrics", instanceId, config, false);
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		
		FetchResult response = subject.call();
		
		Assertions.assertNull(response);
	}
	

}
