package org.cloudfoundry.promregator.endpoint;

import java.io.IOException;
import java.util.HashMap;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.mockServer.DefaultMetricsEndpointHttpHandler;
import org.cloudfoundry.promregator.mockServer.MetricsEndpointMockServer;
import org.cloudfoundry.promregator.textformat004.Parser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = LabelEnrichmentMockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="disabledLabelEnrichment.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
@ActiveProfiles(profiles= {"MetricsEndpointTest"})
class DisabledEnrichmentMetricsEndpointTest {

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
	
	@AfterEach
	void resetMockedHTTPServletRequest() {
		Mockito.reset(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest);
	}
	
	@Autowired
	@Qualifier("promregatorMetricsController") // NB: Otherwise ambiguity with TestableMetricsEndpoint would be a problem - we want "the real one"
	private MetricsEndpoint subject;
	
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testEnrichmentStillTakesPlaceForSingleEndpointScrapingEvenIfDisabledInConfig() {
		Assertions.assertNotNull(subject);
		
		String response = subject.getMetrics().getBody();
		
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
		
		MetricFamilySamples upMFS = mapMFS.get("promregator_up");
		Assertions.assertNotNull(upMFS);
		Assertions.assertEquals(1, upMFS.samples.size());
		
		Sample upSample = upMFS.samples.get(0);
		Assertions.assertEquals(5, upSample.labelNames.size());
		Assertions.assertEquals(5, upSample.labelValues.size());
	}

}
