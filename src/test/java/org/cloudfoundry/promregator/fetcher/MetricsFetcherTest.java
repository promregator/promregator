package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.cloudfoundry.promregator.mockServer.MetricsEndpointMockServer;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.textformat004.TextFormat004Parser;
import org.cloudfoundry.promregator.textformat004.TextFormat004ParserTest;
import org.junit.After;
import org.junit.AfterClass;
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

	@AfterClass
	public static void cleanupEnvironment() {
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
	public void testStraightForward() throws Exception {
		String instanceId = "abcd:4";
		NullMetricFamilySamplesEnricher dummymfse = new NullMetricFamilySamplesEnricher("dummy", "dummy", "dummy", "dummy:0");
		List<String> labelValues = dummymfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, false);
		UUID currentUUID = UUID.randomUUID();
		
		CFMetricsFetcherConfig config = new CFMetricsFetcherConfig();
		config.setMetricFamilySamplesEnricher(dummymfse);
		config.setMetricsFetcherMetrics(mfm);
		config.setPromregatorInstanceIdentifier(currentUUID);
		config.setConnectionTimeoutInMillis(5000);
		config.setSocketReadTimeoutInMillis(5000);
		
		CFMetricsFetcher subject = new CFMetricsFetcher("http://localhost:9002/metrics", instanceId, config);
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		
		HashMap<String, MetricFamilySamples> response = subject.call();
		
		TextFormat004ParserTest.compareEMFS(this.expectedResult, Collections.enumeration(response.values()));
		Assert.assertEquals(instanceId, this.mems.getMetricsEndpointHandler().getHeaders().getFirst("X-CF-APP-INSTANCE"));
		Assert.assertEquals(currentUUID.toString(), this.mems.getMetricsEndpointHandler().getHeaders().getFirst(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER));
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
		
		CFMetricsFetcherConfig config = new CFMetricsFetcherConfig();
		config.setAuthenticationEnricher(ae);
		config.setMetricFamilySamplesEnricher(dummymfse);
		config.setMetricsFetcherMetrics(mfm);
		config.setPromregatorInstanceIdentifier(UUID.randomUUID());
		config.setConnectionTimeoutInMillis(5000);
		config.setSocketReadTimeoutInMillis(5000);

		
		CFMetricsFetcher subject = new CFMetricsFetcher("http://localhost:9002/metrics", instanceId, config);
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		
		HashMap<String, MetricFamilySamples> response = subject.call();
		
		Assert.assertTrue(ae.isCalled());
		
		TextFormat004ParserTest.compareEMFS(this.expectedResult, Collections.enumeration(response.values()));
		Assert.assertEquals(instanceId, this.mems.getMetricsEndpointHandler().getHeaders().getFirst("X-CF-APP-INSTANCE"));
		Assert.assertEquals("Bearer abc", this.mems.getMetricsEndpointHandler().getHeaders().getFirst("Authentication"));
	}
	
	@Test
	public void testSocketReadTimeoutTriggered() throws Exception {
		String instanceId = "abcd:7";
		NullMetricFamilySamplesEnricher dummymfse = new NullMetricFamilySamplesEnricher("dummy", "dummy", "dummy", "dummy:0");
		List<String> labelValues = dummymfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, false);
		UUID currentUUID = UUID.randomUUID();
		
		CFMetricsFetcherConfig config = new CFMetricsFetcherConfig();
		config.setMetricFamilySamplesEnricher(dummymfse);
		config.setMetricsFetcherMetrics(mfm);
		config.setPromregatorInstanceIdentifier(currentUUID);
		config.setConnectionTimeoutInMillis(5000);
		config.setSocketReadTimeoutInMillis(10); // Note that this is way too strict
		
		CFMetricsFetcher subject = new CFMetricsFetcher("http://localhost:9002/metrics", instanceId, config);
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		this.mems.getMetricsEndpointHandler().setDelayInMillis(500);
		
		HashMap<String, MetricFamilySamples> response = subject.call();
		
		Assert.assertNull(response);
	}
	
	@Test
	public void testInvalidEndpointURL() throws Exception {
		String instanceId = "abcd:8";
		NullMetricFamilySamplesEnricher dummymfse = new NullMetricFamilySamplesEnricher("dummy", "dummy", "dummy", "dummy:0");
		List<String> labelValues = dummymfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, false);
		UUID currentUUID = UUID.randomUUID();
		
		CFMetricsFetcherConfig config = new CFMetricsFetcherConfig();
		config.setMetricFamilySamplesEnricher(dummymfse);
		config.setMetricsFetcherMetrics(mfm);
		config.setPromregatorInstanceIdentifier(currentUUID);
		config.setConnectionTimeoutInMillis(5000);
		config.setSocketReadTimeoutInMillis(5000); // Note that this is very strict
		
		CFMetricsFetcher subject = new CFMetricsFetcher("http://localhost:9042/metrics", instanceId, config);
		
		this.mems.getMetricsEndpointHandler().setResponse(DUMMY_METRICS_LIST);
		this.mems.getMetricsEndpointHandler().setDelayInMillis(500);
		
		HashMap<String, MetricFamilySamples> response = subject.call();
		
		Assert.assertNull(response);
	}


}
