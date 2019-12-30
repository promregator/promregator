package org.cloudfoundry.promregator.endpoint;

import java.util.HashMap;
import java.util.UUID;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.fetcher.TextFormat004Parser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
@DirtiesContext(classMode=ClassMode.AFTER_CLASS)
@ActiveProfiles(profiles= {"MetricsEndpointTest"})
public class MetricsEndpointTest {

	@After
	public void resetMockedHTTPServletRequest() {
		Mockito.reset(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest);
	}
	
	@Autowired
	private TestableMetricsEndpoint subject;
	
	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	public void testGetMetrics() {
		Assert.assertNotNull(subject);
		
		String response = subject.getMetrics().getBody();
		
		Assert.assertNotNull(response);
		Assert.assertNotEquals("", response);
		
		TextFormat004Parser parser = new TextFormat004Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assert.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assert.assertNotNull(mapMFS.get("metric_unittestapp2"));
	}
	
	@Test
	public void testIssue52() {
		Assert.assertNotNull(subject);
		
		String response = subject.getMetrics().getBody();
		
		Assert.assertNotNull(response);
		Assert.assertNotEquals("", response);
		
		TextFormat004Parser parser = new TextFormat004Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assert.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assert.assertNotNull(mapMFS.get("metric_unittestapp2"));
		
		MetricFamilySamples mfs = mapMFS.get("promregator_scrape_duration_seconds");
		Assert.assertNotNull(mfs);
		Assert.assertEquals(1, mfs.samples.size());
		
		Sample sample = mfs.samples.get(0);
		Assert.assertTrue(sample.labelNames.isEmpty());
		Assert.assertTrue(sample.labelValues.isEmpty());
	}

	@Test(expected=HttpMessageNotReadableException.class)
	public void testNegativeIsLoopbackScrapingRequest() {
		Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
		.thenReturn(MockedMetricsEndpointSpringApplication.currentPromregatorInstanceIdentifier.toString());
		
		subject.getMetrics();
	}
	
	@Test
	public void testPositiveIsNotALoopbackScrapingRequest() {
		Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
		.thenReturn(UUID.randomUUID().toString());
		
		ResponseEntity<String> result = subject.getMetrics(); // real test: no exception is raised
		
		Assert.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}
}
