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
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class SingleTargetMetricsEndpointTest {

	@Autowired
	private TestableSingleTargetMetricsEndpoint subject;
	
	@Test
	public void testGetMetrics() {
		Assert.assertNotNull(subject);
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0");
		
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
		
		String response = subject.getMetrics("faedbb0a-2273-4cb4-a659-bd31331f7daf", "0");
		
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
		Assert.assertEquals("[org_name, space_name, app_name, cf_instance_id, cf_instance_number]", sample.labelNames.toString()); 
		Assert.assertEquals("[unittestorg, unittestspace, unittestapp, faedbb0a-2273-4cb4-a659-bd31331f7daf:0, 0]", sample.labelValues.toString()); 
	}

}
