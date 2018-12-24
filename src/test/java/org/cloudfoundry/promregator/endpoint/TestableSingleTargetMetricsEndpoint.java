package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.fetcher.SynchronousMetricsFetcher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@RestController
@RequestMapping(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"_test"+"/{applicationId}/{instanceNumber}")
/* 
 * NB: The endpoint's position must be unique. As also the non-testable variant is loaded, this would lead to a 
 * ambiguous mapping exception.
 * We are not using the RequestMapping in our tests anyway, so we can just add some suffix and we are good to go.
 */
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
@Profile("SingleTargetMetricsEndpointTest")
public class TestableSingleTargetMetricsEndpoint extends SingleTargetMetricsEndpoint {

	@Override
	protected List<SynchronousMetricsFetcher> createMetricsFetchers(List<Instance> instanceList) {
		List<SynchronousMetricsFetcher> list = new LinkedList<>();
		
		for (Instance instance : instanceList) {
			MockedMetricsFetcher mf = new MockedMetricsFetcher(instance);
			list.add(mf);
		}
		
		return list;
	}
	
}
