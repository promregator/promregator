package org.cloudfoundry.promregator.endpoint;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;

import org.cloudfoundry.promregator.rewrite.GenericMetricFamilySamplesPrefixRewriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

@RestController
@RequestMapping(EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS)
@Scope(value = WebApplicationContext.SCOPE_REQUEST)
public class PromregatorMetricsEndpoint {
	private static final Logger log = LoggerFactory.getLogger(PromregatorMetricsEndpoint.class);
	
	private GenericMetricFamilySamplesPrefixRewriter gmfspr = new GenericMetricFamilySamplesPrefixRewriter("promregator");

	@GetMapping(produces = TextFormat.CONTENT_TYPE_004)
	public ResponseEntity<String> getMetrics004() {
		return ResponseEntity.badRequest().body("text/plain;version=0.0.4 is no longer supported after Prometheus library simpleclient has dropped supported in version 0.10.0. "
				+ "Using the command line, you may get the metrics using the new format for example by calling \"curl -H 'Accept: application/openmetrics-text; version=1.0.0; charset=utf-8' http://localhost:8080"+ EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS + "\"");
	}
	
	@GetMapping(produces = TextFormat.CONTENT_TYPE_OPENMETRICS_100)
	public String getMetricsOpenMetrics100(CollectorRegistry collectorRegistry) {
		HashMap<String, MetricFamilySamples> mfsMap = this.gmfspr.determineEnumerationOfMetricFamilySamples(collectorRegistry);

		Writer writer = new StringWriter();
		try {
			TextFormat.writeFormat(TextFormat.CONTENT_TYPE_OPENMETRICS_100, writer, Collections.enumeration(mfsMap.values()));
		} catch (IOException e) {
			log.error("Internal error on writing Promregator metrics",  e);
			return null;
		}
		
		return writer.toString();
	}
	
	@GetMapping
	/* 
	 * Fallback case for compatibility: if no "Accept" header is specified or "Accept: * /*",
	 * then we fall back to the classic response.
	 */
	public ResponseEntity<String> getMetricsUnspecified() {
		return this.getMetrics004();
	}
}
