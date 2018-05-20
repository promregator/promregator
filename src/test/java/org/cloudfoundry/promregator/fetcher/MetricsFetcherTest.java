package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.mockServer.MetricsEndpointMockServer;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.prometheus.client.Collector.MetricFamilySamples;

public class MetricsFetcherTest {

	private static final String DUMMY_METRICS_LIST = "# HELP dummy This is a dummy metric\n"+
			"# TYPE dummy counter\n"+
			"dummy 42 1395066363000";
	private MetricsEndpointMockServer mems;
	
	private Enumeration<MetricFamilySamples> expectedResult;
	
	public MetricsFetcherTest() {
		this.expectedResult = Collections.enumeration(new TextFormat004Parser(DUMMY_METRICS_LIST).parse().values());
	}
	
	@Before
	public void startUpMetricsEndpointServer() throws IOException {
		this.mems = new MetricsEndpointMockServer();
		this.mems.start();
	}
	
	@After
	public void tearDownMetricsEndpointServer() {
		this.mems.stop();
	}

	private static class NullMetricFamilySamplesEnricher extends CFMetricFamilySamplesEnricher {

		public NullMetricFamilySamplesEnricher(String orgName, String spaceName, String appName, String instance) {
			super(orgName, spaceName, appName, instance);
		}

		@Override
		public HashMap<String, MetricFamilySamples> determineEnumerationOfMetricFamilySamples(HashMap<String, MetricFamilySamples> mfs) {
			return mfs;
		}
	}
	
	@Test
	public void testStraightForward() throws Exception {
		String instanceId = "abcd:4";
		NullMetricFamilySamplesEnricher dummymfse = new NullMetricFamilySamplesEnricher("dummy", "dummy", "dummy", "dummy:0");
		List<String> labelValues = dummymfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, false);
		CFMetricsFetcher subject = new CFMetricsFetcher("http://localhost:9002/metrics", instanceId, null, dummymfse, mfm, null, UUID.randomUUID());
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		
		HashMap<String, MetricFamilySamples> response = subject.call();
		
		TextFormat004ParserTest.compareEMFS(this.expectedResult, Collections.enumeration(response.values()));
		Assert.assertEquals(instanceId, this.mems.getMetricsEndpointHandler().getHeaders().getFirst("X-CF-APP-INSTANCE"));
	}
	
	private static class TestAuthenticationEnricher implements AuthenticationEnricher {
		private boolean called = false;
		
		@Override
		public void enrichWithAuthentication(HttpGet httpget) {
			this.called = true;
			
			Assert.assertEquals("/metrics", httpget.getURI().getPath());
			
			httpget.addHeader("Authentication", "Bearer abc");
		}

		public boolean isCalled() {
			return called;
		}
	}
	
	@Test
	public void testAEIsCalled() throws Exception {
		String instanceId = "abcd:2";
		TestAuthenticationEnricher ae = new TestAuthenticationEnricher();
		NullMetricFamilySamplesEnricher dummymfse = new NullMetricFamilySamplesEnricher("dummy", "dummy", "dummy", "dummy:0");
		List<String> labelValues = dummymfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, false);
		CFMetricsFetcher subject = new CFMetricsFetcher("http://localhost:9002/metrics", instanceId, ae, dummymfse, mfm, null, UUID.randomUUID());
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		
		HashMap<String, MetricFamilySamples> response = subject.call();
		
		Assert.assertTrue(ae.isCalled());
		
		TextFormat004ParserTest.compareEMFS(this.expectedResult, Collections.enumeration(response.values()));
		Assert.assertEquals(instanceId, this.mems.getMetricsEndpointHandler().getHeaders().getFirst("X-CF-APP-INSTANCE"));
		Assert.assertEquals("Bearer abc", this.mems.getMetricsEndpointHandler().getHeaders().getFirst("Authentication"));
	}

}
