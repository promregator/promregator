package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.mockServer.MetricsEndpointMockServerTLS;
import org.cloudfoundry.promregator.rewrite.OwnMetricsEnrichmentLabelVector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
	
	@Test
	void testPKIXErrorOnSelfSignedCertificateInDefaultMode() throws Exception {
		String instanceId = "abcd:4";
		OwnMetricsEnrichmentLabelVector omelv = new OwnMetricsEnrichmentLabelVector(null, "dummy", "dummy", "dummy", "dummy:0");
		List<String> labelValues = omelv.getEnrichedLabelValues();
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, false, omelv);
		UUID currentUUID = UUID.randomUUID();
		
		CFMetricsFetcherConfig config = new CFMetricsFetcherConfig();
		config.setMetricsFetcherMetrics(mfm);
		config.setPromregatorInstanceIdentifier(currentUUID);
		
		final CFMetricsFetcherConnManager connManager = new CFMetricsFetcherConnManager(5000, 5000, 10000, null, 0);
		config.setCfMetricsFetcherConnManager(connManager);
		
		CFMetricsFetcher subject = new CFMetricsFetcher("https://localhost:9003/metrics", instanceId, config, false);
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		
		FetchResult response = subject.call();
		
		Assertions.assertNull(response);
	}
	

}
