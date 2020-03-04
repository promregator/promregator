package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.time.Instant;
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
public class SingleTargetMetricsEndpoint {
	private static final Logger log = LoggerFactory.getLogger(SingleTargetMetricsEndpoint.class);

	private final boolean simulationMode;
	private final String proxyHost;
	private final int proxyPort;
	private final int maxProcessingTime;
	private final boolean recordRequestLatency;
	private int fetcherConnectionTimeout;
	private int fetcherSocketReadTimeout;

	private final ExecutorService metricsFetcherPool;
	private final AuthenticatorController authenticatorController;
	private final UUID promregatorInstanceIdentifier;
	private final InstanceCache instanceCache;
	
	private GenericMetricFamilySamplesPrefixRewriter gmfspr = new GenericMetricFamilySamplesPrefixRewriter("promregator");

	/* own metrics --- specific to this (scraping) request */
	private CollectorRegistry requestRegistry;
	
	// see also https://prometheus.io/docs/instrumenting/writing_exporters/#metrics-about-the-scrape-itself
	private Gauge up;

	public SingleTargetMetricsEndpoint(@Value("${promregator.simulation.enabled:false}") boolean simulationMode,
									   ExecutorService metricsFetcherPool,
									   AuthenticatorController authenticatorController,
									   @Value("${promregator.scraping.proxy.host:@null}") String proxyHost,
									   @Value("${promregator.scraping.proxy.port:0}") int proxyPort,
									   @Value("${promregator.scraping.maxProcessingTime:5000}") int maxProcessingTime,
									   @Value("${promregator.metrics.requestLatency:false}") boolean recordRequestLatency,
									   @Value("${promregator.scraping.connectionTimeout:5000}") int fetcherConnectionTimeout,
									   @Value("${promregator.scraping.socketReadTimeout:5000}") int fetcherSocketReadTimeout,
									   UUID promregatorInstanceIdentifier,
									   InstanceCache instanceCache) {
		this.simulationMode = simulationMode;
		this.metricsFetcherPool = metricsFetcherPool;
		this.authenticatorController = authenticatorController;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.maxProcessingTime = maxProcessingTime;
		this.recordRequestLatency = recordRequestLatency;
		this.fetcherConnectionTimeout = fetcherConnectionTimeout;
		this.fetcherSocketReadTimeout = fetcherSocketReadTimeout;
		this.promregatorInstanceIdentifier = promregatorInstanceIdentifier;
		this.instanceCache = instanceCache;

		setupOwnRequestScopedMetrics();
		validateAndFixFetcherTimeouts();
	}

	public void setupOwnRequestScopedMetrics() {
		this.requestRegistry = new CollectorRegistry();
		
		Builder builder = Gauge.build("promregator_up", "Indicator, whether the target of promregator is available");
		
		builder = builder.labelNames(NullMetricFamilySamplesEnricher.getEnrichingLabelNames());
		
		this.up = builder.register(this.requestRegistry);
	}
	
	public void validateAndFixFetcherTimeouts() {
		long localMaxProcessingTime = this.maxProcessingTime;
		
		if (this.fetcherConnectionTimeout > localMaxProcessingTime) {
			log.warn("Fetcher's Connection Timeout is longer than the configured Maximal Processing Time of all fetchers; shortening timeout value to that value, as this does not make sense. "+
					"Check your configured values for configuration options promregator.scraping.connectionTimeout and promregator.scraping.maxProcessingTime respectively promregator.endpoint.maxProcessingTime (deprecated)");
			this.fetcherConnectionTimeout = (int) localMaxProcessingTime;
		}
		
		if (this.fetcherSocketReadTimeout > localMaxProcessingTime) {
			log.warn("Fetcher's Socket Read Timeout is longer than the configured Maximal Processing Time of all fetchers; shortening timeout value to that value, as this does not make sense. "+
					"Check your configured values for configuration options promregator.scraping.socketReadTimeout and promregator.scraping.maxProcessingTime respectively promregator.endpoint.maxProcessingTime (deprecated)");
			this.fetcherSocketReadTimeout = (int) localMaxProcessingTime;
		}
	}
	
	@RequestMapping(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/{hash}")
	@GetMapping(produces=TextFormat.CONTENT_TYPE_004)
	public ResponseEntity<String> getMetrics(
			@PathVariable String hash,
			HttpServletRequest request
			) {
		
		if (this.isLoopbackRequest(request)) {
			throw new LoopbackScrapingDetectedException("Erroneous Loopback Scraping request detected");
		}
		
		final Instance instance = this.instanceCache.getCachedInstance(hash);
		
		if (instance == null) {
			return new ResponseEntity<>("Invalid hash value provided; no instance detected", HttpStatus.NOT_FOUND);
		}
		
		String response = null;
		try {
			response = this.handleRequest(instance);
		} catch (ScrapingException e) {
			return new ResponseEntity<>(e.toString(), HttpStatus.NOT_FOUND);
		}
		
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	public String handleRequest(Instance instance) throws ScrapingException {
		log.debug("Received request to metrics endpoint");
		Instant start = Instant.now();
		
		this.up.clear();
		
		MetricsFetcher mf = this.createMetricsFetcher(instance);
		if (mf == null) {
			throw new ScrapingException("No MetricFetcher could be determined");
		}
		
		Future<HashMap<String, MetricFamilySamples>> future = this.startMetricsFetchers(mf);
		
		MergableMetricFamilySamples mmfs = waitForMetricsFetcher(future);
		
		Instant stop = Instant.now();
		Duration duration = Duration.between(start, stop);
		
		Gauge scrapeDuration = Gauge.build("promregator_scrape_duration_seconds", "Duration in seconds indicating how long scraping of all metrics took")
				.register(this.requestRegistry);
		
		scrapeDuration.set(duration.toMillis() / 1000.0);

		// add also our own request-specific metrics
		mmfs.merge(this.gmfspr.determineEnumerationOfMetricFamilySamples(this.requestRegistry));
		
		return mmfs.toType004String();
	}

	private MergableMetricFamilySamples waitForMetricsFetcher(Future<HashMap<String, MetricFamilySamples>> future) {
		MergableMetricFamilySamples mmfs = new MergableMetricFamilySamples();
		
		try {
			HashMap<String, MetricFamilySamples> emfs = future.get(this.maxProcessingTime, TimeUnit.MILLISECONDS);
			
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
			log.info("Data could be scraped within the current promregator.scraping.maxProcessingTime. "
					+ "Consider increasing promregator.scraping.maxProcessingTime.");
		}
		
		return mmfs;
	}

	private Future<HashMap<String, MetricFamilySamples>> startMetricsFetchers(MetricsFetcher mf) {
		Future<HashMap<String, MetricFamilySamples>> future = this.metricsFetcherPool.submit(mf);
		return future;
	}

	protected MetricsFetcher createMetricsFetcher(Instance instance) {
		
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
		
		
		/*
		 * Warning! the gauge "up" is a very special beast!
		 * As it is always transferred along the other metrics (it's not a promregator-own metric!), it must always
		 * follow the same labels as the other metrics which are scraped
		 */
		Gauge.Child upChild = null;
		AbstractMetricFamilySamplesEnricher mfse = new NullMetricFamilySamplesEnricher();
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
			
			if (this.proxyHost != null && this.proxyPort != 0) {
				// using the new way
				cfmfConfig.setProxyHost(this.proxyHost);
				cfmfConfig.setProxyPort(this.proxyPort);
			}
			
			mf = new CFMetricsFetcher(accessURL, instance.getInstanceId(), cfmfConfig);
		}
		
		return mf;
	}
	
	private String[] determineOwnTelemetryLabelValues(String orgName, String spaceName, String appName, String instanceId) {
		AbstractMetricFamilySamplesEnricher mfse = new CFAllLabelsMetricFamilySamplesEnricher(orgName, spaceName, appName, instanceId);
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		
		return labelValues.toArray(new String[0]);
	}
	
	/**
	 * verifies if the current HTTP request is coming from the same Promregator instance 
	 * (and thus we would have a loopback / recursive scraping request). This situation needs to be prohibited
	 * as it might lead to an endless loop.
	 * @return <code>true</code>, if a loopback was detected (which case the current request should be aborted); 
	 * <code>false</code> otherwise.
	 */
	public boolean isLoopbackRequest(HttpServletRequest request) {
		if (request == null) {
			log.warn("Missing HTTP Servlet request reference; unable to verify whether this is a loopback request or not");
			return false;
		}
		
		String headerValue = request.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER);
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

}
