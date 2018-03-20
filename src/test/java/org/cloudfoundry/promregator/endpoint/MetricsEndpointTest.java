package org.cloudfoundry.promregator.endpoint;

import java.util.HashMap;

import org.cloudfoundry.promregator.fetcher.TextFormat004Parser;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.prometheus.client.Collector.MetricFamilySamples;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class MetricsEndpointTest {

	@Autowired
	private TestableMetricsEndpoint subject;
	
	@Test
	public void testGetMetrics() {
		Assert.assertNotNull(subject);
		
		String response = subject.getMetrics();
		
		Assert.assertNotNull(response);
		Assert.assertNotEquals("", response);
		
		TextFormat004Parser parser = new TextFormat004Parser(response);
		HashMap<String, MetricFamilySamples> mapMFS = parser.parse();
		
		Assert.assertNotNull(mapMFS.get("metric_unittestapp"));
		Assert.assertNotNull(mapMFS.get("metric_unittestapp2"));
	}

}
