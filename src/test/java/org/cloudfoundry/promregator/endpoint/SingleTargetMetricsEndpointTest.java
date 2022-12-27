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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.prometheus.client.Collector.MetricFamilySamples;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
@ActiveProfiles("SingleTargetMetricsEndpointTest")
class SingleTargetMetricsEndpointTest {

	@AfterEach
	void resetMockedHTTPServletRequest() {
		Mockito.reset(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest);
	}
	
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Autowired
	private TestableSingleTargetMetricsEndpoint subject;
	
	@Test
	void testGetMetrics() {
		Assertions.assertNotNull(subject);
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0").getBody();
		
		Assertions.assertNotNull(response);
		Assertions.assertNotEquals("", response);
		
		Parser parser = new Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assertions.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assertions.assertNull(mapMFS.get("metric_unittestapp2"));
	}
	
	@Test
	void testNegativeIsLoopbackScrapingRequest() {
		Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
		.thenReturn(MockedMetricsEndpointSpringApplication.currentPromregatorInstanceIdentifier.toString());

		Assertions.assertThrows(LoopbackScrapingDetectedException.class, () -> {
			subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0");
		});
	}
	
	@Test
	void testPositiveIsNotALoopbackScrapingRequest() {
		Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
		.thenReturn(UUID.randomUUID().toString());
		
		ResponseEntity<String> result = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0"); // real test: no exception is raised
		
		Assertions.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}

}
