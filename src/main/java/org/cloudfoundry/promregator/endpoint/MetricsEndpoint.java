package org.cloudfoundry.promregator.endpoint;

import java.util.List;

import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.prometheus.client.exporter.common.TextFormat;

/**
 * A spring-framework HTTP REST-server endpoint, compliant to the specification of a Prometheus text (!) metrics endpoint,
 * whose data is being backed by a set of further Prometheus metrics endpoints run on one or several CF apps. 
 *
 */
@RestController
@RequestMapping("/metrics")
public class MetricsEndpoint extends AbstractMetricsEndpoint {
	
	@RequestMapping(method = RequestMethod.GET, produces=TextFormat.CONTENT_TYPE_004)
	public String getMetrics() {
		return this.handleRequest();
	}

	@Override
	protected boolean isIncludeGlobalMetrics() {
		return true;
	}

	@Override
	protected List<Instance> filterInstanceList(List<Instance> instanceList) {
		// all instances shall be processed
		return instanceList; 
	}
	
}
