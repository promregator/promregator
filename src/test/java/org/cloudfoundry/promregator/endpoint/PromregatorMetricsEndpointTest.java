package org.cloudfoundry.promregator.endpoint;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;

class PromregatorMetricsEndpointTest {

	@Test
	void getMetrics004() {
		PromregatorMetricsEndpoint subject = new PromregatorMetricsEndpoint();
		
		ResponseEntity<String> responseEntity = subject.getMetrics004();
		
		Assertions.assertEquals(400, responseEntity.getStatusCode().value());
	}
	
	@Test
	void getMetricsUnspecified() {
		PromregatorMetricsEndpoint subject = new PromregatorMetricsEndpoint();
		
		ResponseEntity<String> responseEntity = subject.getMetricsUnspecified();
		
		Assertions.assertEquals(400, responseEntity.getStatusCode().value());
	}
	
	@Test
	void getMetricsTextFormat100() {
		PromregatorMetricsEndpoint subject = new PromregatorMetricsEndpoint();
		
		final Collector collector = new Collector() {
			@Override
			public List<MetricFamilySamples> collect() {
				Sample sample = new Sample("test", List.of(), List.of(), 1.0);
				MetricFamilySamples mfs = new MetricFamilySamples("test", Type.GAUGE, "help", List.of(sample));
				return List.of(mfs);
			}
			
		};
		
		CollectorRegistry.defaultRegistry.register(collector);
		
		final String response;
		try {
			response = subject.getMetricsOpenMetrics100();
		} finally {
			CollectorRegistry.defaultRegistry.unregister(collector);
		}
		
		Assertions.assertTrue(Pattern.compile("^promregator_test 1.0", Pattern.MULTILINE).matcher(response).find());
	}

}
