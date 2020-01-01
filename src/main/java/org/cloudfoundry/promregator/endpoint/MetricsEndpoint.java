package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.util.List;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
@RequestMapping(EndpointConstants.ENDPOINT_PATH_SINGLE_ENDPOINT_SCRAPING)
public class MetricsEndpoint extends AbstractMetricsEndpoint {
	private static final Logger log = Logger.getLogger(MetricsEndpoint.class);

	@GetMapping(produces=TextFormat.CONTENT_TYPE_004)
	public ResponseEntity<String> getMetrics() {
		if (this.isLoopbackRequest()) {
			throw new LoopbackScrapingDetectedException("Erroneous Loopback Scraping request detected");
		}
		try {
			String result = this.handleRequest(null, null /* no filtering intended */);
			return new ResponseEntity<>(result, HttpStatus.OK);
		} catch (ScrapingException e) {
			return new ResponseEntity<>(e.toString(), HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	@Override
	protected boolean isIncludeGlobalMetrics() {
		return true;
	}

	@Override
	protected boolean isLabelEnrichmentSuppressable() {
		/*
		 * We may have metrics of multiple different Cloud Foundry instances in our response. 
		 * If label enrichment would be suppressed, this could mix up all the data.
		 * That is why we must prevent that label enrichment is suppressed (hence, answering "false").
		 */
		return false;
	}
	

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.endpoint.AbstractMetricsEndpoint#createMetricsFetchers(java.util.List)
	 */
	@Override
	protected List<MetricsFetcher> createMetricsFetchers(List<Instance> instanceList) {
		if (instanceList.size() > 20) {
			log.warn(String.format("You are using Single Endpoint Scraping with %d (>20) active targets; to improve scalability it is recommended to switch to Single Target Scraping", instanceList.size()));
		}

		return super.createMetricsFetchers(instanceList);
	}

	@Override
	protected void handleScrapeDuration(CollectorRegistry requestRegistry, Duration duration) {
		/* Note:
		 * The metric in this method intends to describe how much time has passed 
		 * for the *entire* scraping process. It's not the intention of this metric
		 * to drill-down to each scraping request.
		 * That is why it does not have additional labels, i.e. it does not have labels
		 * which are depending on the target.
		 * 
		 * See also the description of "promregator_scrape_duration_seconds" in
		 * https://github.com/promregator/promregator/blob/4b2ca289b624328e7e0b3838112e31a908a55c58/docs/enrichment.md
		 */
		Gauge scrapeDuration = Gauge.build("promregator_scrape_duration_seconds", "Duration in seconds indicating how long scraping of all metrics took")
				.register(requestRegistry);
		
		scrapeDuration.set(duration.toMillis() / 1000.0);
	}

}
