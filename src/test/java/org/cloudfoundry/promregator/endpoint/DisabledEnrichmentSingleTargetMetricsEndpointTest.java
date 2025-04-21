package org.cloudfoundry.promregator.endpoint;

import java.io.IOException;
import java.util.regex.Pattern;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.mockServer.DefaultMetricsEndpointHttpHandler;
import org.cloudfoundry.promregator.mockServer.MetricsEndpointMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = LabelEnrichmentMockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="disabledLabelEnrichment.properties")
public class DisabledEnrichmentSingleTargetMetricsEndpointTest {

	private static MetricsEndpointMockServer mockServer;

	@BeforeAll
	static void startMockedTargetMetricsEndpoint() throws IOException {
		mockServer = new MetricsEndpointMockServer();
		DefaultMetricsEndpointHttpHandler meh = mockServer.getMetricsEndpointHandler();
		meh.setResponse("""
				# HELP dummy This is a dummy metric
				# TYPE dummy counter
				dummy{label="xyz"} 42 1395066363000""");
		
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
	void testGetMetricsLabelsAreCorrectIfLabelEnrichmentIsDisabled() {
		Assertions.assertNotNull(subject);
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0").getBody();
		
		Assertions.assertNotNull(response);
		Assertions.assertNotEquals("", response);
		
		Assertions.assertTrue(Pattern.compile("^dummy\\{label=\"xyz\"\\} 42 1395066363000", Pattern.MULTILINE).matcher(response).find());
		
	
		Assertions.assertTrue(Pattern.compile("^promregator_up 1.0", Pattern.MULTILINE).matcher(response).find());
		Assertions.assertTrue(Pattern.compile("^promregator_scrape_duration_seconds ", Pattern.MULTILINE).matcher(response).find());
		
	}
	
}
