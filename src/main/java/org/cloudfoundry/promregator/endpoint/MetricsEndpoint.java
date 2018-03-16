package org.cloudfoundry.promregator.endpoint;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.BasicAuthenticationEnricher;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.auth.OAuth2XSUAAEnricher;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.GenericMetricFamilySamplesPrefixRewriter;
import org.cloudfoundry.promregator.rewrite.MFSUtils;
import org.cloudfoundry.promregator.rewrite.MergableMetricFamilySamples;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * A spring-framework HTTP REST-server endpoint, compliant to the specification of a Prometheus text (!) metrics endpoint,
 * whose data is being backed by a set of further Prometheus metrics endpoints run on one or several CF apps. 
 * The data is simply aggregated 1:1 without paying attention overlap and redundancy.
 *
 */
@RestController
@RequestMapping("/metrics")
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
public class MetricsEndpoint {
	
	private static final Logger log = Logger.getLogger(MetricsEndpoint.class);
	
	@Autowired
	private ReactiveAppInstanceScanner reactiveAppInstanceScanner;
	
	@Autowired
	private ExecutorService metricsFetcherPool;
	
	@Autowired
	private CollectorRegistry collectorRegistry;
	
	@Value("${cf.proxyHost:@null}")
	private String proxyHost;
	
	@Value("${cf.proxyPort:0}")
	private int proxyPort;

	@Value("${promregator.endpoint.maxProcessingTime:5000}")
	private int maxProcessingTime;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;
	
	private AuthenticationEnricher ae;
	private GenericMetricFamilySamplesPrefixRewriter gmfspr = new GenericMetricFamilySamplesPrefixRewriter("promregator");
	
	/* own metrics --- static scope (e.g. across all requests) */
	private static Histogram requestLatency = Histogram.build("promregator_request_latency", "The latency, which the targets of the promregator produce")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	
	private static Counter failedRequests = Counter.build("promregator_request_failure", "Requests, which responded, but the HTTP code indicated an error or the connection dropped/timed out")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	
	
	/* own metrics --- specific to this (scraping) request */
	
	private CollectorRegistry requestRegistry;
	
	// see also https://prometheus.io/docs/instrumenting/writing_exporters/#metrics-about-the-scrape-itself
	private Gauge scrape_duration;
	private Gauge up;
	
	@PostConstruct
	public void setupOwnRequestScopedMetrics() {
		this.requestRegistry = new CollectorRegistry();
		
		this.scrape_duration = Gauge.build("promregator_scrape_duration_seconds", "Duration in seconds indicating how long scraping of all metrics took")
				.register(this.requestRegistry);
		
		this.up = Gauge.build("promregator_up", "Indicator, whether the target of promregator is available")
				.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
				.register(this.requestRegistry);
	}
	
	@PostConstruct
	public void setupAuthenticationEnricher() {
		String type = promregatorConfiguration.getAuthenticator().getType();
		if ("OAuth2XSUAA".equalsIgnoreCase(type)) {
			this.ae = new OAuth2XSUAAEnricher(this.promregatorConfiguration.getAuthenticator().getOauth2xsuaa());
		} else if ("none".equalsIgnoreCase(type) || "null".equalsIgnoreCase(type)) {
			this.ae = new NullEnricher();
		} else if ("basic".equalsIgnoreCase(type)) {
			this.ae = new BasicAuthenticationEnricher(this.promregatorConfiguration.getAuthenticator().getBasic());
		} else {
			log.warn(String.format("Authenticator type %s is unknown; skipping", type));
		}
	}
	
	@RequestMapping(method = RequestMethod.GET, produces=TextFormat.CONTENT_TYPE_004)
	public String getMetrics() {
		Instant start = Instant.now();
		
		this.up.clear();
		
		List<MetricsFetcher> callablesPrep = this.createMetricFetchers();
		
		LinkedList<Future<HashMap<String,MetricFamilySamples>>> futures = new LinkedList<>();
		for (MetricsFetcher mf : callablesPrep) {
			Future<HashMap<String, MetricFamilySamples>> future = this.metricsFetcherPool.submit(mf);
			
			futures.add(future);
		}
		
		long starttime = System.currentTimeMillis();
		
		MergableMetricFamilySamples mmfs = new MergableMetricFamilySamples();
		
		for (Future<HashMap<String, MetricFamilySamples>> future : futures) {
			long maxWaitTime = starttime + this.maxProcessingTime - System.currentTimeMillis();
			
			try {
				if (maxWaitTime < 0 && !future.isDone()) {
					// only process those, which are already completed
					continue;
				}
				HashMap<String, MetricFamilySamples> emfs = future.get(maxWaitTime, TimeUnit.MILLISECONDS);
				
				if (emfs != null) {
					mmfs.merge(emfs);
				}
			} catch (InterruptedException e) {
				continue;
			} catch (ExecutionException e) {
				log.warn("Exception thrown while fetching Metrics data from target", e);
				continue;
			} catch (TimeoutException e) {
				log.info("Timeout while fetching metrics data from target", e);
				continue; // process the other's as well!
			}
			
		}
		Instant stop = Instant.now();
		Duration duration = Duration.between(start, stop);
		this.scrape_duration.set(duration.toMillis() / 1000.0);
		// NB: We have to set this here now, otherwise it would not be added to the collectorRegistry properly.
		/* 
		 * NB: This is a little bit off w.r.t parallel requests: If two requests came
		 * in and reached this pint exactly at the same point time, then the two values
		 * could be mixed up. 
		 * But: in practice this most likely would not have any major influence
		 */
		
		// also add our own metrics
		Enumeration<MetricFamilySamples> rawMFS = this.collectorRegistry.metricFamilySamples();
		HashMap<String, MetricFamilySamples> enrichedMFS = this.gmfspr.determineEnumerationOfMetricFamilySamples(MFSUtils.convertToEMFSToHashMap(rawMFS));
		mmfs.merge(enrichedMFS);
		
		// add also our own request specific metrics
		rawMFS = this.requestRegistry.metricFamilySamples();
		enrichedMFS = this.gmfspr.determineEnumerationOfMetricFamilySamples(MFSUtils.convertToEMFSToHashMap(rawMFS));
		mmfs.merge(enrichedMFS);
		
		// serialize
		Enumeration<MetricFamilySamples> resultEMFS = mmfs.getEnumerationMetricFamilySamples();
		Writer writer = new StringWriter();
		try {
			TextFormat.write004(writer, resultEMFS);
		} catch (IOException e) {
			log.error("IO Exception on StringWriter; uuuhhh...", e);
		}
		
		return writer.toString();
	}

	private List<MetricsFetcher> createMetricFetchers() {
		
		List<Instance> instanceList = this.reactiveAppInstanceScanner.determineInstancesFromTargets(this.promregatorConfiguration.getTargets());
		
		List<MetricsFetcher> callablesPrep = new LinkedList<MetricsFetcher>();
		for (Instance instance : instanceList) {
			log.info(String.format("Instance %s", instance.instanceId));
			String orgName = instance.target.getOrgName();
			String spaceName = instance.target.getSpaceName();
			String appName = instance.target.getApplicationName();
			
			String accessURL = instance.accessUrl;
			
			if (accessURL == null) {
				log.warn(String.format("Unable to retrieve hostname for %s/%s/%s; skipping", orgName, spaceName, appName));
				continue;
			}
			
			AbstractMetricFamilySamplesEnricher mfse = new CFMetricFamilySamplesEnricher(orgName, spaceName, appName, instance.instanceId);
			String[] labelNamesForOwnMetrics = { orgName, spaceName, appName, instance.instanceId, CFMetricFamilySamplesEnricher.getInstanceFromInstanceId(instance.instanceId) };

			MetricsFetcher mf = null;
			if (this.proxyHost != null && this.proxyPort != 0) {
				mf = new MetricsFetcher(accessURL, instance.instanceId, this.ae, mfse, this.proxyHost, this.proxyPort, labelNamesForOwnMetrics, requestLatency, this.up, failedRequests);
			} else {
				mf = new MetricsFetcher(accessURL, instance.instanceId, this.ae, mfse, labelNamesForOwnMetrics, requestLatency, this.up, failedRequests);
			}
			callablesPrep.add(mf);
		}
		
		return callablesPrep;
	}
}
