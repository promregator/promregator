package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.fetcher.CFMetricsFetcher;
import org.cloudfoundry.promregator.fetcher.CFMetricsFetcherConfig;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherSimulator;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.GenericMetricFamilySamplesPrefixRewriter;
import org.cloudfoundry.promregator.rewrite.MergableMetricFamilySamples;
import org.cloudfoundry.promregator.rewrite.NullMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
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

@RestController
@Scope(value=WebApplicationContext.SCOPE_REQUEST) // see also https://github.com/promregator/promregator/issues/51
@RequestMapping(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/{applicationId}/{instanceNumber}")
public class SingleTargetMetricsEndpoint {
	
	private static final Logger log = LoggerFactory.getLogger(SingleTargetMetricsEndpoint.class);

	private Instance instance;
	
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
	
	@Value("${promregator.scraping.labelEnrichment:true}")
	private boolean labelEnrichment;

	@Value("${promregator.scraping.connectionTimeout:5000}")
	private int fetcherConnectionTimeout;

	@Value("${promregator.scraping.socketReadTimeout:5000}")
	private int fetcherSocketReadTimeout;
	
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
		
		if (this.labelEnrichment) {
			builder = builder.labelNames(CFAllLabelsMetricFamilySamplesEnricher.getEnrichingLabelNames());
		} else {
			builder = builder.labelNames(NullMetricFamilySamplesEnricher.getEnrichingLabelNames());
		}
		
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
	
	protected String handleRequest(String applicationId, String instanceId) throws ScrapingException {
		log.debug("Received request to a metrics endpoint");
		Instant start = Instant.now();
		
		this.up.clear();
		
		List<Instance> instanceList = this.cfDiscoverer.discover(discoveredApplicationId -> applicationId.equals(discoveredApplicationId), requestInstance -> {
			if (requestInstance.getInstanceId().equals(instanceId)) {
				this.instance = requestInstance;
				return true;
			}
			
			return false;
		});
		
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
		
		Future<HashMap<String, MetricFamilySamples>> future = this.metricsFetcherPool.submit(mf);
		
		MergableMetricFamilySamples mmfs = waitForMetricsFetcher(future);
		
		Instant stop = Instant.now();
		Duration duration = Duration.between(start, stop);
		this.handleScrapeDuration(this.requestRegistry, duration);
		
		// add also our own request-specific metrics
		mmfs.merge(this.gmfspr.determineEnumerationOfMetricFamilySamples(this.requestRegistry));
		
		return mmfs.toType004String();
	}

	private MergableMetricFamilySamples waitForMetricsFetcher(Future<HashMap<String, MetricFamilySamples>> future) {
		final long starttime = System.currentTimeMillis();
		
		MergableMetricFamilySamples mmfs = new MergableMetricFamilySamples();
		
		final long maxWaitTime = starttime + this.maxProcessingTime - System.currentTimeMillis();

		try {
			HashMap<String, MetricFamilySamples> emfs = future.get(maxWaitTime, TimeUnit.MILLISECONDS);
			
			if (emfs != null) {
				mmfs.merge(emfs);
			}
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
		
		return mmfs;
	}

	protected MetricsFetcher createMetricsFetcher(final Instance instance) {
		
		log.debug(String.format("Creating Metrics Fetcher for instance %s", instance.getInstanceId()));
		
		ResolvedTarget target = instance.getTarget();
		String orgName = target.getOrgName();
		String spaceName = target.getSpaceName();
		String appName = target.getApplicationName();
		
		String accessURL = instance.getAccessUrl();
		
		if (accessURL == null) {
			log.warn(String.format("Unable to retrieve hostname for %s/%s/%s; skipping", orgName, spaceName, appName));
			return null;
		}
		
		String[] ownTelemetryLabelValues = this.determineOwnTelemetryLabelValues(orgName, spaceName, appName, instance.getInstanceId());
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, this.recordRequestLatency);
		
		final boolean labelEnrichmentEnabled = this.labelEnrichment;
		
		/*
		 * Warning! the gauge "up" is a very special beast!
		 * As it is always transferred along the other metrics (it's not a promregator-own metric!), it must always
		 * follow the same labels as the other metrics which are scraped
		 */
		Gauge.Child upChild = null;
		AbstractMetricFamilySamplesEnricher mfse = null;
		if (labelEnrichmentEnabled) {
			mfse = new CFAllLabelsMetricFamilySamplesEnricher(orgName, spaceName, appName, instance.getInstanceId());
		} else {
			mfse = new NullMetricFamilySamplesEnricher();
		}
		upChild = this.up.labels(mfse.getEnrichedLabelValues(new LinkedList<>()).toArray(new String[0]));
		
		AuthenticationEnricher ae = this.authenticatorController.getAuthenticationEnricherByTarget(instance.getTarget().getOriginalTarget());
		
		MetricsFetcher mf = null;
		if (this.simulationMode) {
			mf = new MetricsFetcherSimulator(accessURL, ae, mfse, mfm, upChild);
		} else {
			CFMetricsFetcherConfig cfmfConfig = new CFMetricsFetcherConfig();
			cfmfConfig.setAuthenticationEnricher(ae);
			cfmfConfig.setMetricFamilySamplesEnricher(mfse);
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
	
	private String[] determineOwnTelemetryLabelValues(String orgName, String spaceName, String appName, String instanceId) {
		AbstractMetricFamilySamplesEnricher mfse = new CFAllLabelsMetricFamilySamplesEnricher(orgName, spaceName, appName, instanceId);
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		
		return labelValues.toArray(new String[0]);
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
	
	@GetMapping(produces=TextFormat.CONTENT_TYPE_004)
	public ResponseEntity<String> getMetrics(
			@PathVariable String applicationId,
			@PathVariable String instanceNumber
			) {
		
		if (this.isLoopbackRequest()) {
			throw new LoopbackScrapingDetectedException("Erroneous Loopback Scraping request detected");
		}
		
		final String instanceId = String.format("%s:%s", applicationId, instanceNumber);
		
		String response = null;
		try {
			response = this.handleRequest(applicationId, instanceId);
		} catch (ScrapingException e) {
			log.debug(String.format("ScrapingException was raised for instanceid %s", instanceId), e);
			return new ResponseEntity<>(e.toString(), HttpStatus.NOT_FOUND);
		}
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	
	/**
	 * called when scraping has been finished; contains the overall duration of the scraping request.
	 * 
	 * The implementing class is suggested to write the duration into an own sample for the corresponding
	 * metric.
	 * @param requestRegistry the registry to which the metric shall be / is registered.
	 * @param duration the duration of the just completed scrape request.
	 */
	private void handleScrapeDuration(CollectorRegistry requestRegistry, Duration duration) {
		/*
		 * Note: The scrape_duration_seconds metric is being passed on to Prometheus with
		 * the normal scraping request.
		 * If the configuration option promregator.scraping.labelEnrichment is disabled, then 
		 * the metric must also comply to this approach. Otherwise there might arise issues
		 * with rewriting in Prometheus.
		 */
		
		AbstractMetricFamilySamplesEnricher enricher = null;
		String[] ownTelemetryLabels = null;
		if (this.labelEnrichment) {
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
		
		Gauge scrapeDuration = Gauge.build("promregator_scrape_duration_seconds", "Duration in seconds indicating how long scraping of all metrics took")
				.labelNames(ownTelemetryLabels)
				.register(requestRegistry);
		
		List<String> labelValues = enricher.getEnrichedLabelValues(new ArrayList<>(0));
		scrapeDuration.labels(labelValues.toArray(new String[0])).set(duration.toMillis() / 1000.0);
	}
}
