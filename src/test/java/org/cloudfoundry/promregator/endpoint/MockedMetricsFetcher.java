package org.cloudfoundry.promregator.endpoint;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.scanner.Instance;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

public class MockedMetricsFetcher implements MetricsFetcher {
	private Instance instance;
	
	public MockedMetricsFetcher(Instance instance) {
		this.instance = instance;
	}

	@Override
	public Map<String, MetricFamilySamples> call() throws Exception {
		Map<String, MetricFamilySamples> result = new HashMap<>();
		
		String metricName = "metric_"+this.instance.getTarget().getApplicationName();
		
		
		LinkedList<String> labelNames = new LinkedList<>();
		labelNames.add("instanceId");
		
		LinkedList<String> labelValues = new LinkedList<>();
		labelValues.add(this.instance.getInstanceId());
		
		Sample s = new Sample(metricName, labelNames, labelValues, 1.0);
		
		List<Sample> samples = new LinkedList<>();
		samples.add(s);
		MetricFamilySamples mfs = new MetricFamilySamples(metricName, Type.GAUGE, "dummyhelp", samples);
		
		result.put(metricName, mfs);
		
		return result;
	}
	
}