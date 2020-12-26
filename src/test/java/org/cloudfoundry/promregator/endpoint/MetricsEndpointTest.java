package org.cloudfoundry.promregator.endpoint;

import java.util.HashMap;
import java.util.UUID;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.textformat004.Parser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
@ActiveProfiles(profiles= {"MetricsEndpointTest"})
class MetricsEndpointTest {

	@AfterEach
	void resetMockedHTTPServletRequest() {
		Mockito.reset(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest);
	}
	
	@Autowired
	private TestableMetricsEndpoint subject;
	
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testGetMetrics() {
		Assertions.assertNotNull(subject);
		
		String response = subject.getMetrics().getBody();
		
		Assertions.assertNotNull(response);
		Assertions.assertNotEquals("", response);
		
		Parser parser = new Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assertions.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assertions.assertNotNull(mapMFS.get("metric_unittestapp2"));
	}
	
	@Test
	void testIssue52() {
		Assertions.assertNotNull(subject);
		
		String response = subject.getMetrics().getBody();
		
		Assertions.assertNotNull(response);
		Assertions.assertNotEquals("", response);
		
		Parser parser = new Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assertions.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assertions.assertNotNull(mapMFS.get("metric_unittestapp2"));
		
		MetricFamilySamples mfs = mapMFS.get("promregator_scrape_duration_seconds");
		Assertions.assertNotNull(mfs);
		Assertions.assertEquals(1, mfs.samples.size());
		
		Sample sample = mfs.samples.get(0);
		Assertions.assertTrue(sample.labelNames.isEmpty());
		Assertions.assertTrue(sample.labelValues.isEmpty());
	}

	@Test
	void testNegativeIsLoopbackScrapingRequest() {
		Assertions.assertThrows(LoopbackScrapingDetectedException.class, () -> {
			Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
			.thenReturn(MockedMetricsEndpointSpringApplication.currentPromregatorInstanceIdentifier.toString());
			
			subject.getMetrics();
		});
	}
	
	@Test
	void testPositiveIsNotALoopbackScrapingRequest() {
		Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
		.thenReturn(UUID.randomUUID().toString());
		
		ResponseEntity<String> result = subject.getMetrics(); // real test: no exception is raised
		
		Assertions.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}
}
