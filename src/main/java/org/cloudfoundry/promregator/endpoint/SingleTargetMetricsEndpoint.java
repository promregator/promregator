package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.NullMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

@RestController
@Scope(value=WebApplicationContext.SCOPE_REQUEST) // see also https://github.com/promregator/promregator/issues/51
@RequestMapping(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/{applicationId}/{instanceNumber}")
public class SingleTargetMetricsEndpoint extends AbstractMetricsEndpoint {
	
	private static final Logger log = Logger.getLogger(SingleTargetMetricsEndpoint.class);

	private Instance instance;
	
	@GetMapping(produces=TextFormat.CONTENT_TYPE_004)
	public ResponseEntity<String> getMetrics(
			@PathVariable String applicationId, 
			@PathVariable String instanceNumber
			) {
		
		if (this.isLoopbackRequest()) {
			throw new HttpMessageNotReadableException("Errornous Loopback Scraping request detected");
		}
		
		String instanceId = String.format("%s:%s", applicationId, instanceNumber);
		
		String response = null;
		try {
			response = this.handleRequest( discoveredApplicationId -> applicationId.equals(discoveredApplicationId)
			, instance -> {
				if (instance.getInstanceId().equals(instanceId)) {
					this.instance = instance;
					return true;
				}
				
				return false;
			});
		} catch (ScrapingException e) {
			return new ResponseEntity<>(e.toString(), HttpStatus.NOT_FOUND);
		}
		
		return new ResponseEntity<String>(response, HttpStatus.OK);
	}

	@Override
	protected boolean isIncludeGlobalMetrics() {
		// NB: This is done by PromregatorMetricsEndpoint in this scenario instead.
		return false;
	}

	@Override
	protected boolean isLabelEnrichmentSuppressable() {
		/*
		 * we only have metrics of a single Cloud Foundry application instance in our
		 * response. Thus, it is permitted that label enrichment may be suppressed (hence answering "true" here).
		 */
		return true;
	}
	
	@Override
	protected void handleScrapeDuration(CollectorRegistry requestRegistry, Duration duration) {
		/*
		 * Note: The scrape_duration_seconds metric is being passed on to Prometheus with
		 * the normal scraping request.
		 * If the configuration option promregator.scraping.labelEnrichment is disabled, then 
		 * the metric must also comply to this approach. Otherwise there might arise issues
		 * with rewriting in Prometheus.
		 */
		
		AbstractMetricFamilySamplesEnricher enricher = null;
		String[] ownTelemetryLabels = null;
		if (this.isLabelEnrichmentEnabled()) {
			if (this.instance == null) {
				log.warn("Internal inconsistency: Single Target Metrics Endpoint triggered, even though instance could not be detected; skipping scrape_duration");
				return;
			}
			
			ResolvedTarget t = this.instance.getTarget();
			ownTelemetryLabels = CFAllLabelsMetricFamilySamplesEnricher.getEnrichingLabelNames();
			enricher = new CFAllLabelsMetricFamilySamplesEnricher(t.getOrgName(), t.getSpaceName(), t.getApplicationName(), this.instance.getInstanceId());
		} else {
			ownTelemetryLabels = NullMetricFamilySamplesEnricher.getEnrichingLabelNames();
			enricher = new NullMetricFamilySamplesEnricher();
		}
		
		Gauge scrape_duration = Gauge.build("promregator_scrape_duration_seconds", "Duration in seconds indicating how long scraping of all metrics took")
				.labelNames(ownTelemetryLabels)
				.register(requestRegistry);
		
		List<String> labelValues = enricher.getEnrichedLabelValues(new ArrayList<String>(0));
		scrape_duration.labels(labelValues.toArray(new String[0])).set(duration.toMillis() / 1000.0);
	}

	
}
