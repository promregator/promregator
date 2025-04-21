package org.cloudfoundry.promregator.endpoint;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.fetcher.CFMetricsFetcher;
import org.cloudfoundry.promregator.fetcher.CFMetricsFetcherConfig;
import org.cloudfoundry.promregator.fetcher.FetchResult;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherSimulator;
import org.cloudfoundry.promregator.rewrite.GenericMetricFamilySamplesPrefixRewriter;
import org.cloudfoundry.promregator.rewrite.MetricSetMerger;
import org.cloudfoundry.promregator.rewrite.OwnMetricsEnrichmentLabelVector;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Builder;
import io.prometheus.client.exporter.common.TextFormat;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@Scope(value=WebApplicationContext.SCOPE_REQUEST) // see also https://github.com/promregator/promregator/issues/51
@RequestMapping(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/{applicationId}/{instanceNumber}")
public class SingleTargetMetricsEndpoint {
	
	private static final Logger log = LoggerFactory.getLogger(SingleTargetMetricsEndpoint.class);
	
	private static final Pattern PATTERN_APPLICATION_ID_FORMAT = Pattern.compile("[-0-9a-f]++");

	@Value("${promregator.simulation.enabled:false}")
	private boolean simulationMode;
	
	@Autowired
	private ExecutorService metricsFetcherPool;
	
	@Autowired
	private CFMultiDiscoverer cfDiscoverer;

	@Autowired
	private AuthenticatorController authenticatorController;
	
	@Value("${promregator.scraping.proxy.host:@null}")
	private String proxyHost;
	
	@Value("${promregator.scraping.proxy.port:0}")
	private int proxyPort;

	/**
	 * The maximal processing time permitted for Scraping (in milliseconds).
	 */
	@Value("${promregator.scraping.maxProcessingTime:5000}")
	private int maxProcessingTime;
	
	@Value("${promregator.metrics.requestLatency:false}")
	private boolean recordRequestLatency;
	
	@Value("${promregator.scraping.connectionTimeout:5000}")
	private int fetcherConnectionTimeout;

	@Value("${promregator.scraping.socketReadTimeout:5000}")
	private int fetcherSocketReadTimeout;
	
	@Value("${promregator.metrics.labelNamePrefix:#{null}}")
	private String ownMetricsLabelNamePrefix;
	
	@Autowired
	private UUID promregatorInstanceIdentifier;
	
	@Autowired
	private HttpServletRequest httpServletRequest;
	
	private GenericMetricFamilySamplesPrefixRewriter gmfspr = new GenericMetricFamilySamplesPrefixRewriter("promregator");
	
	/* own metrics --- specific to this (scraping) request */
	private CollectorRegistry requestRegistry;
	
	// see also https://prometheus.io/docs/instrumenting/writing_exporters/#metrics-about-the-scrape-itself
	private Gauge up;
	
	@PostConstruct
	public void setupOwnRequestScopedMetrics() {
		this.requestRegistry = new CollectorRegistry();
		
		Builder builder = Gauge.build("promregator_up", "Indicator, whether the target of promregator is available");
		
		this.up = builder.register(this.requestRegistry);
	}
	
	@PostConstruct
	public void validateAndFixFetcherTimeouts() {
		long localMaxProcessingTime = this.maxProcessingTime;
		
		if (this.fetcherConnectionTimeout > localMaxProcessingTime) {
			log.warn("Fetcher's Connection Timeout is longer than the configured Maximal Processing Time of all fetchers; shortening timeout value to that value, as this does not make sense. "+
					"Check your configured values for configuration options promregator.scraping.connectionTimeout and promregator.scraping.maxProcessingTime");
			this.fetcherConnectionTimeout = (int) localMaxProcessingTime;
		}
		
		if (this.fetcherSocketReadTimeout > localMaxProcessingTime) {
			log.warn("Fetcher's Socket Read Timeout is longer than the configured Maximal Processing Time of all fetchers; shortening timeout value to that value, as this does not make sense. "+
					"Check your configured values for configuration options promregator.scraping.socketReadTimeout and promregator.scraping.maxProcessingTime");
			this.fetcherSocketReadTimeout = (int) localMaxProcessingTime;
		}
	}
	
	// protected due to unit tests!
	protected FetchResult handleRequest(String applicationId, String instanceId) throws ScrapingException {
		log.debug("Received request to a metrics endpoint");
		Instant start = Instant.now();
		
		this.up.clear();
		
		List<Instance> instanceList = this.cfDiscoverer.discover(discoveredApplicationId -> applicationId.equals(discoveredApplicationId), 
				requestInstance -> requestInstance.getInstanceId().equals(instanceId));
		
		if (instanceList == null || instanceList.isEmpty()) {
			throw new ScrapingException("Unable to determine any instance to scrape");
		}
		
		if (instanceList.size() > 1) {
			throw new ScrapingException("Unexpected duplication of Instance returned");
		}
		Instance instance = instanceList.get(0);
		
		MetricsFetcher mf = this.createMetricsFetcher(instance);
		if (mf == null) {
			throw new ScrapingException("Unable to create MetricsFetcher");
		}
		
		Future<FetchResult> future = this.metricsFetcherPool.submit(mf);
		
		FetchResult fetchResult = waitForMetricsFetcher(future);
		
		if (fetchResult == null) {
			fetchResult = new FetchResult("", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		}
		
		Instant stop = Instant.now();
		Duration duration = Duration.between(start, stop);
		
		/*
		 * Note: The scrape_duration_seconds metric is being passed on to Prometheus with
		 * the normal scraping request.
		 */
		Gauge scrapeDuration = Gauge.build("promregator_scrape_duration_seconds", "Duration in seconds indicating how long scraping of all metrics took")
				.unit("seconds")
				.register(requestRegistry);
		
		scrapeDuration.set(duration.toMillis() / 1000.0);
		
		// add also our own request-specific metrics
		final String enrichedMetricsSet = this.mergeInternalMetricsWithFetchResult(fetchResult, applicationId, instanceId);
		
		return new FetchResult(enrichedMetricsSet, fetchResult.contentType());
	}

	private FetchResult waitForMetricsFetcher(Future<FetchResult> future) {
		final long starttime = System.currentTimeMillis();
		
		final long maxWaitTime = starttime + this.maxProcessingTime - System.currentTimeMillis();

		try {
			FetchResult fetchResult = future.get(maxWaitTime, TimeUnit.MILLISECONDS);
			return fetchResult;
		} catch (InterruptedException e) {
			log.warn("Interrupted unexpectedly", e);
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			log.warn("Exception thrown while fetching Metrics data from target", e);
			// continue not necessary here
		} catch (TimeoutException e) {
			log.info("Not all targets could be scraped within the current promregator.scraping.maxProcessingTime. "
					+ "Consider increasing promregator.scraping.maxProcessingTime or promregator.scraping.threads, "
					+ "but mind the implications. See also https://github.com/promregator/promregator/wiki/Handling-Timeouts-on-Scraping");
			// continue not necessary here - other's shall and are still processed
		}
		return null;
	}

	protected MetricsFetcher createMetricsFetcher(final Instance instance) {
		
		log.debug("Creating Metrics Fetcher for instance {}", instance.getInstanceId());
		
		ResolvedTarget target = instance.getTarget();
		String orgName = target.getOrgName();
		String spaceName = target.getSpaceName();
		String appName = target.getApplicationName();
		
		String accessURL = instance.getAccessUrl();
		
		if (accessURL == null) {
			log.warn("Unable to retrieve hostname for {}/{}/{}; skipping", orgName, spaceName, appName);
			return null;
		}
		
		OwnMetricsEnrichmentLabelVector omelv = new OwnMetricsEnrichmentLabelVector(this.ownMetricsLabelNamePrefix, orgName, spaceName, appName, instance.getInstanceId());
		List<String> labelValues = omelv.getEnrichedLabelValues();
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, this.recordRequestLatency, omelv);
		
		/*
		 * Warning! the gauge "up" is a very special beast!
		 * As it is always transferred along the other metrics (it's not a promregator-own metric!), it must always
		 * follow the same labels as the other metrics which are scraped
		 */
		Gauge.Child upChild = null;
		
		upChild = this.up.labels();
		
		AuthenticationEnricher ae = this.authenticatorController.getAuthenticationEnricherByTarget(instance.getTarget().getOriginalTarget());
		
		MetricsFetcher mf = null;
		if (this.simulationMode) {
			mf = new MetricsFetcherSimulator(accessURL, ae, mfm, upChild);
		} else {
			CFMetricsFetcherConfig cfmfConfig = new CFMetricsFetcherConfig();
			cfmfConfig.setAuthenticationEnricher(ae);
			cfmfConfig.setMetricsFetcherMetrics(mfm);
			cfmfConfig.setUpChild(upChild);
			cfmfConfig.setPromregatorInstanceIdentifier(this.promregatorInstanceIdentifier);
			cfmfConfig.setConnectionTimeoutInMillis(this.fetcherConnectionTimeout);
			cfmfConfig.setSocketReadTimeoutInMillis(this.fetcherSocketReadTimeout);
			this.provideProxyConfiguration(cfmfConfig);
			
			mf = new CFMetricsFetcher(accessURL, instance.getInstanceId(), cfmfConfig, instance.isInternal());
		}
		
		return mf;
	}
	
	private String mergeInternalMetricsWithFetchResult(FetchResult fetchResult, String applicationId, String instanceId) {
		final String fetchData = fetchResult.data();
		
		HashMap<String, MetricFamilySamples> mapMFS = this.gmfspr.determineEnumerationOfMetricFamilySamples(this.requestRegistry);
		
		for (String metricName : mapMFS.keySet()) {
			Pattern pType = Pattern.compile("^# TYPE +%s +".formatted(metricName));
			if (pType.matcher(fetchData).find()) {
				log.warn("Instance {} of application {} emitted a metric {}, which is reserved by Promregator. Skipping adding Promregator's metrics", instanceId, applicationId, metricName);
				return fetchData;
			}
			
			Pattern pMetric = Pattern.compile("^%s *\\{".formatted(metricName));
			if (pMetric.matcher(fetchData).find()) {
				log.warn("Instance {} of application {} emitted a sample with name {}, which is reserved by Promregator. Skipping adding Promregator's metrics", instanceId, applicationId, metricName);
				return fetchData;
			}
		}
		
		Writer writer = new StringWriter();
		try {
			TextFormat.writeFormat(fetchResult.contentType(), writer, Collections.enumeration(mapMFS.values()));
		} catch (IOException e) {
			log.error("Internal error on writing internal metrics for instance {} of application {}", instanceId, applicationId, e);
			return fetchData;
		}
		
		return new MetricSetMerger(fetchResult, writer.toString()).merge();
	}

	
	private void provideProxyConfiguration(CFMetricsFetcherConfig cfmfConfig) {
		String effectiveProxyHost = this.proxyHost;
		int effectiveProxyPort = this.proxyPort;
		
		if (effectiveProxyHost != null && effectiveProxyPort != 0) {
			cfmfConfig.setProxyHost(effectiveProxyHost);
			cfmfConfig.setProxyPort(effectiveProxyPort);
		}
	}
	
	/**
	 * verifies if the current HTTP request is coming from the same Promregator instance 
	 * (and thus we would have a loopback / recursive scraping request). This situation needs to be prohibited
	 * as it might lead to an endless loop.
	 * @return <code>true</code>, if a loopback) was detected (which case the current request should be aborted); 
	 * <code>false</code> otherwise.
	 */
	public boolean isLoopbackRequest() {
		if (this.httpServletRequest == null) {
			log.warn("Missing HTTP Servlet request reference; unable to verify whether this is a loopback request or not");
			return false;
		}
		
		String headerValue = this.httpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER);
		if (headerValue == null) {
			// the header was not set - so this can't be a Promregator instance anyway
			return false;
		}
		
		boolean loopback = this.promregatorInstanceIdentifier.toString().equals(headerValue);
		
		if (loopback) {
			log.error("Erroneous loopback request detected. One of your targets is improperly pointing back to Promregator itself. Please revise your configuration!");
		}
		
		return loopback;
	}
	
	private ResponseEntity<String> performPrechecks(String applicationId,  String instanceNumber) {
		if (this.isLoopbackRequest()) {
			throw new LoopbackScrapingDetectedException("Erroneous Loopback Scraping request detected");
		}
		
		/* perform input validation */
		try {
			Integer.parseInt(instanceNumber);
		} catch (NumberFormatException e) {
			return new ResponseEntity<>("Invalid Instance Number provided", HttpStatus.BAD_REQUEST);
		}
		
		if (!PATTERN_APPLICATION_ID_FORMAT.matcher(applicationId).matches()) {
			return new ResponseEntity<>("Invalid Application Id provided", HttpStatus.BAD_REQUEST);
		}
		
		return null;
	}
	
	@GetMapping
	public ResponseEntity<String> getMetrics(
			@PathVariable String applicationId,
			@PathVariable String instanceNumber
			) {
		
		ResponseEntity<String> precheckResults = this.performPrechecks(applicationId, instanceNumber);
		if (precheckResults != null) {
			return precheckResults;
		}
		
		final String instanceId = "%s:%s".formatted(applicationId, instanceNumber);
		
		FetchResult response = null;
		try {
			response = this.handleRequest(applicationId, instanceId);
		} catch (ScrapingException e) {
			log.debug("ScrapingException was raised for instanceid {}", instanceId, e);
			return new ResponseEntity<>(e.toString(), HttpStatus.NOT_FOUND);
		}
		
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, response.contentType())
				.body(response.data());
	}
}
