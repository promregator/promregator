package org.cloudfoundry.promregator.endpoint;

import java.util.List;

import org.apache.log4j.Logger;
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
	private static final Logger log = Logger.getLogger(MetricsEndpoint.class);

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
		
		if (instanceList.size() > 20) {
			log.warn(String.format("You are using Single Endpoint Scraping with %d (>20) active targets; to improve scalability it is recommended to switch to Single Target Scraping", instanceList.size()));
		}
		
		return instanceList; 
	}
	
}
