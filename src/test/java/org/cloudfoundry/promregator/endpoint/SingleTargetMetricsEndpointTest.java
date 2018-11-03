package org.cloudfoundry.promregator.endpoint;

import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cloudfoundry.promregator.fetcher.TextFormat004Parser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
@ActiveProfiles("SingleTargetMetricsEndpointTest")
public class SingleTargetMetricsEndpointTest {

	@After
	public void resetMockedHTTPServletRequest() {
		Mockito.reset(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest);
	}
	
	@Autowired
	private TestableSingleTargetMetricsEndpoint subject;
	
	@Test
	public void testGetMetrics() {
		Assert.assertNotNull(subject);
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0").getBody();
		
		Assert.assertNotNull(response);
		Assert.assertNotEquals("", response);
		
		TextFormat004Parser parser = new TextFormat004Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assert.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assert.assertNull(mapMFS.get("metric_unittestapp2"));
	}
	
	@Test
	public void testIssue52() {
		Assert.assertNotNull(subject);
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0").getBody();
		
		Assert.assertNotNull(response);
		Assert.assertNotEquals("", response);
		
		TextFormat004Parser parser = new TextFormat004Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assert.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assert.assertNull(mapMFS.get("metric_unittestapp2"));
		
		MetricFamilySamples mfs = mapMFS.get("promregator_scrape_duration_seconds");
		Assert.assertNotNull(mfs);
		Assert.assertEquals(1, mfs.samples.size());
		
		Sample sample = mfs.samples.get(0);
		Assert.assertEquals("[instance, org_name, space_name, app_name, cf_instance_id, cf_instance_number]", sample.labelNames.toString()); 
		Assert.assertEquals("[faedbb0a-2273-4cb4-a659-bd31331f7daf:0, unittestorg, unittestspace, unittestapp, faedbb0a-2273-4cb4-a659-bd31331f7daf:0, 0]", sample.labelValues.toString()); 
	}
	
	@Test
	public void testIssue51() {
		Assert.assertNotNull(subject);
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0").getBody();
		
		Assert.assertNotNull(response);
		Assert.assertNotEquals("", response);

		final Pattern p = Pattern.compile("cf_instance_id=\"([^\"]+)\"");
		
		Matcher m = p.matcher(response);
		boolean atLeastOneFound = false;
		while(m.find()) {
			atLeastOneFound = true;
			String instanceId = m.group(1);
			Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:0", instanceId);
		}
		Assert.assertTrue(atLeastOneFound);
	}
	
	@Test(expected=HttpMessageNotReadableException.class)
	public void testNegativeIsLoopbackScrapingRequest() {
		Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
		.thenReturn(MockedMetricsEndpointSpringApplication.currentPromregatorInstanceIdentifier.toString());
		
		subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0");
	}
	
	@Test
	public void testPositiveIsNotALoopbackScrapingRequest() {
		Mockito.when(MockedMetricsEndpointSpringApplication.mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER))
		.thenReturn(UUID.randomUUID().toString());
		
		subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0");
	}

}
