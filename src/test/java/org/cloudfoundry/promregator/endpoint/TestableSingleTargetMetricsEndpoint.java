package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@RestController
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
@Profile("SingleTargetMetricsEndpointTest")
public class TestableSingleTargetMetricsEndpoint extends SingleTargetMetricsEndpoint {

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
