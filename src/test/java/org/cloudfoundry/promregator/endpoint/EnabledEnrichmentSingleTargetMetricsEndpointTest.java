package org.cloudfoundry.promregator.endpoint;

import java.io.IOException;
import java.util.HashMap;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.mockServer.DefaultMetricsEndpointHttpHandler;
import org.cloudfoundry.promregator.mockServer.MetricsEndpointMockServer;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.textformat004.Parser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = LabelEnrichmentMockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="enabledLabelEnrichment.properties")
class EnabledEnrichmentSingleTargetMetricsEndpointTest {

	private static MetricsEndpointMockServer mockServer;

	@BeforeAll
	static void startMockedTargetMetricsEndpoint() throws IOException {
		mockServer = new MetricsEndpointMockServer();
		DefaultMetricsEndpointHttpHandler meh = mockServer.getMetricsEndpointHandler();
		meh.setResponse("# HELP dummy This is a dummy metric\n"+
				"# TYPE dummy counter\n"+
				"dummy{label=\"xyz\"} 42 1395066363000");
		
		mockServer.start();
	}
	
	@AfterAll
	static void stopMockedTargetMetricsEndpoint() {
		mockServer.stop();
	}
	
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	
	@Autowired
	@Qualifier("singleTargetMetricsEndpoint") // NB: Otherwise ambiguity with TestableSingleTargetMetricsEndpoint would be a problem - we want "the real one"
	private SingleTargetMetricsEndpoint subject;
	
	@Test
	void testGetMetricsLabelsAreCorrectIfLabelEnrichmentIsEnabled() {
		Assertions.assertNotNull(subject);
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0").getBody();
		
		Assertions.assertNotNull(response);
		Assertions.assertNotEquals("", response);
		
		Parser parser = new Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		MetricFamilySamples dummyMFS = mapMFS.get("dummy_total");
		Assertions.assertNotNull(dummyMFS);
		
		Assertions.assertEquals(1, dummyMFS.samples.size());
		
		Sample dummySample = dummyMFS.samples.get(0);
		Assertions.assertNotNull(dummySample);
		
		Assertions.assertEquals(6, dummySample.labelNames.size());
		Assertions.assertEquals(6, dummySample.labelValues.size());
		
		int indexOfLabel = dummySample.labelNames.indexOf("label");
		Assertions.assertNotEquals(-1, indexOfLabel);
		Assertions.assertEquals("xyz", dummySample.labelValues.get(indexOfLabel));

		int indexOfInstance = dummySample.labelNames.indexOf("instance");
		Assertions.assertEquals(-1, indexOfInstance);
		/* Note:
		 * Prometheus does not permit to set the instance as label via scraping.
		 * The label value may only be changed by rewriting.
		 * See also https://www.robustperception.io/controlling-the-instance-label
		 */

		int indexOfOrgName = dummySample.labelNames.indexOf(CFAllLabelsMetricFamilySamplesEnricher.LABELNAME_ORGNAME);
		Assertions.assertNotEquals(-1, indexOfOrgName);
		Assertions.assertEquals("unittestorg", dummySample.labelValues.get(indexOfOrgName));

		
		MetricFamilySamples upMFS = mapMFS.get("promregator_up");
		Assertions.assertNotNull(upMFS);
		Assertions.assertEquals(1, upMFS.samples.size());
		
		Sample upSample = upMFS.samples.get(0);
		Assertions.assertEquals(5, upSample.labelNames.size());
		Assertions.assertEquals(5, upSample.labelValues.size());
		
		MetricFamilySamples scrapeDurationMFS = mapMFS.get("promregator_scrape_duration_seconds");
		Assertions.assertNotNull(scrapeDurationMFS);
		Assertions.assertEquals(1, scrapeDurationMFS.samples.size());
		
		Sample scrapeDurationSample = scrapeDurationMFS.samples.get(0);
		
		// NB: as the duration is part of the usual scraping response, it also must comply to the rules
		// if labelEnrichment is enabled.

		indexOfInstance = dummySample.labelNames.indexOf("instance");
		Assertions.assertEquals(-1, indexOfInstance);
		/* Note:
		 * Prometheus does not permit to set the instance as label via scraping.
		 * The label value may only be changed by rewriting.
		 * See also https://www.robustperception.io/controlling-the-instance-label
		 */
		
		indexOfOrgName = scrapeDurationSample.labelNames.indexOf(CFAllLabelsMetricFamilySamplesEnricher.LABELNAME_ORGNAME);
		Assertions.assertNotEquals(-1, indexOfOrgName);
		Assertions.assertEquals("unittestorg", scrapeDurationSample.labelValues.get(indexOfOrgName));
	}
	
}
