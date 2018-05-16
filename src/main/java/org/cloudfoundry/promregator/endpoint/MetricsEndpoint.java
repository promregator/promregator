package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.util.List;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * A spring-framework HTTP REST-server endpoint, compliant to the specification of a Prometheus text (!) metrics endpoint,
 * whose data is being backed by a set of further Prometheus metrics endpoints run on one or several CF apps. 
 *
 */
@RestController
@Scope(value=WebApplicationContext.SCOPE_REQUEST) // see also https://github.com/promregator/promregator/issues/51
@RequestMapping("/metrics")
public class MetricsEndpoint extends AbstractMetricsEndpoint {
	private static final Logger log = Logger.getLogger(MetricsEndpoint.class);

	@RequestMapping(method = RequestMethod.GET, produces=TextFormat.CONTENT_TYPE_004)
	public String getMetrics() {
		return this.handleRequest(null /* no filtering intended */);
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

	@Override
	protected void handleScrapeDuration(CollectorRegistry requestRegistry, Duration duration) {
		Gauge scrape_duration = Gauge.build("promregator_scrape_duration_seconds", "Duration in seconds indicating how long scraping of all metrics took")
				.register(requestRegistry);
		
		scrape_duration.set(duration.toMillis() / 1000.0);
	}
	
}
