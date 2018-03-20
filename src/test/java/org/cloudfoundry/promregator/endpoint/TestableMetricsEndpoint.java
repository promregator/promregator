package org.cloudfoundry.promregator.endpoint;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;

@RestController
@RequestMapping("/metrics")
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
public class TestableMetricsEndpoint extends MetricsEndpoint {

	public class MockedMetricsFetcher implements MetricsFetcher {
		private Instance instance;
		
		public MockedMetricsFetcher(Instance instance) {
			this.instance = instance;
		}

		@Override
		public HashMap<String, MetricFamilySamples> call() throws Exception {
			HashMap<String, MetricFamilySamples> result = new HashMap<>();
			
			String metricName = "metric_"+this.instance.getTarget().getApplicationName();
			
			
			LinkedList<String> labelNames = new LinkedList<String>();
			labelNames.add("instanceId");
			
			LinkedList<String> labelValues = new LinkedList<String>();
			labelValues.add(this.instance.getInstanceId());
			
			Sample s = new Sample(metricName, labelNames, labelValues, 1.0);
			
			List<Sample> samples = new LinkedList<>();
			samples.add(s);
			MetricFamilySamples mfs = new MetricFamilySamples(metricName, Type.GAUGE, "dummyhelp", samples);
			
			result.put(metricName, mfs);
			
			return result;
		}
		
	}
	
	@Override
	protected List<MetricsFetcher> createMetricsFetchers(List<Instance> instanceList) {
		List<MetricsFetcher> list = new LinkedList<>();
		
		for (Instance instance : instanceList) {
			MockedMetricsFetcher mf = new MockedMetricsFetcher(instance);
			list.add(mf);
		}
		
		return list;
	}
	
}
