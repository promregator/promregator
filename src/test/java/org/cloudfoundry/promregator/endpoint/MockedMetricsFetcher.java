package org.cloudfoundry.promregator.endpoint;

import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import org.cloudfoundry.promregator.fetcher.FetchResult;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.scanner.Instance;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.exporter.common.TextFormat;

public class MockedMetricsFetcher implements MetricsFetcher {
	private Instance instance;
	
	public MockedMetricsFetcher(Instance instance) {
		this.instance = instance;
	}

	@Override
	public FetchResult call() throws Exception {
		String metricName = "metric_"+this.instance.getTarget().getApplicationName();
		
		Sample s = new Sample(metricName, List.of("instanceId"), List.of(this.instance.getInstanceId()), 1.0);
		MetricFamilySamples mfs = new MetricFamilySamples(metricName, Type.GAUGE, "dummyhelp", List.of(s));

		StringWriter stringWriter = new StringWriter();
		TextFormat.writeOpenMetrics100(stringWriter, Collections.enumeration(List.of(mfs)));
		
		return new FetchResult(stringWriter.toString(), TextFormat.CONTENT_TYPE_OPENMETRICS_100);
	}
	
}