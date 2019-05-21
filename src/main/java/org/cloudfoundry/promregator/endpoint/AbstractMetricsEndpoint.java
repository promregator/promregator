package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Null;

import org.apache.log4j.Logger;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Builder;

/**
 * An abstract class allowing to easily build a spring-framework HTTP REST-server endpoint, 
 * compliant to the specification of a Prometheus text (!) metrics endpoint,
 * whose data is being backed by a set of further Prometheus metrics endpoints run on one or several CF apps. 
 *
 */

// Warning! Setting @Scope(value=WebApplicationContext.SCOPE_REQUEST) here is useless, as it is
// being ignored by the Framework. Workaround: Annotate the implementing class instead
public abstract class AbstractMetricsEndpoint {
	
	private static final Logger log = Logger.getLogger(AbstractMetricsEndpoint.class);
	
	@Value("${promregator.simulation.enabled:false}")
	private boolean simulationMode;
	
	@Autowired
	private ExecutorService metricsFetcherPool;
	
	@Autowired
	private CollectorRegistry collectorRegistry;
	
	@Autowired
	private CFMultiDiscoverer cfDiscoverer;

	@Autowired
	private AuthenticatorController authenticatorController;
	
	@Value("${cf.proxyHost:@null}")
	private String proxyHost;
	
	@Value("${cf.proxyPort:0}")
	private int proxyPort;

	@Value("${promregator.endpoint.maxProcessingTime:#{null}}")
	@Deprecated
	/**
	 * use maxProcessingTime instead
	 */
	private Optional<Integer> maxProcessingTimeOld;

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
		
		if (this.isLabelEnrichmentEnabled()) {
			builder = builder.labelNames(CFAllLabelsMetricFamilySamplesEnricher.getEnrichingLabelNames());
		} else {
			builder = builder.labelNames(NullMetricFamilySamplesEnricher.getEnrichingLabelNames());
		}
		
		this.up = builder.register(this.requestRegistry);
	}
	
	@PostConstruct
	public void warnOnDeprecatedMaxProcessingTime() {
		if (this.maxProcessingTimeOld.isPresent()) {
			log.warn("You are still using the deprecated option promregator.endpoint.maxProcessingTime. "
					+ "Please switch to promregator.scraping.maxProcessingTime (same meaning) instead and remove the old one.");
		}
	}
	
	@PostConstruct
	public void validateAndFixFetcherTimeouts() {
		long localMaxProcessingTime = this.getMaxProcessingTime();
		
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
	
	public String handleRequest(@Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) throws ScrapingException {
		log.debug("Received request to a metrics endpoint");
		Instant start = Instant.now();
		
		this.up.clear();
		
		List<Instance> instanceList = this.cfDiscoverer.discover(applicationIdFilter, instanceFilter);
		
		if (instanceList == null || instanceList.isEmpty()) {
			throw new ScrapingException("Unable to determine any instance to scrape");
		}
		
		List<MetricsFetcher> callablesPrep = this.createMetricsFetchers(instanceList);
		
		LinkedList<Future<HashMap<String, MetricFamilySamples>>> futures = this.startMetricsFetchers(callablesPrep);
		log.debug(String.format("Fetching metrics from %d distinct endpoints", futures.size()));
		
		MergableMetricFamilySamples mmfs = waitForMetricsFetchers(futures);
		
		Instant stop = Instant.now();
		Duration duration = Duration.between(start, stop);
		this.handleScrapeDuration(this.requestRegistry, duration);
		
		if (this.isIncludeGlobalMetrics()) {
			// also add our own (global) metrics
			mmfs.merge(this.gmfspr.determineEnumerationOfMetricFamilySamples(this.collectorRegistry));
		}
		
		// add also our own request-specific metrics
		mmfs.merge(this.gmfspr.determineEnumerationOfMetricFamilySamples(this.requestRegistry));
		
		return mmfs.toType004String();
	}

	/**
	 * called when scraping has been finished; contains the overall duration of the scraping request.
	 * 
	 * The implementing class is suggested to write the duration into an own sample for the corresponding
	 * metric.
	 * @param requestRegistry the registry to which the metric shall be / is registered.
	 * @param duration the duration of the just completed scrape request.
	 */
	protected abstract void handleScrapeDuration(CollectorRegistry requestRegistry, Duration duration);

	/**
	 * specifies whether the global metrics provided by Promregator itself
	 * shall be added to the response of the scraping request
	 * @return <code>true</code> if the global metrics shall be added in the response; 
	 * <code>false</code> otherwise.
	 */
	protected abstract boolean isIncludeGlobalMetrics();
	
	/**
	 * specifies whether it is permitted that the enrichment of labels may be suppressed ("true") or not ("false"). 
	 * 
	 * If (and only if) this method returns <code>true</code> AND promregator.scraping.labelEnrichment is set to false, 
	 * then label enrichment is suppressed.
	 * @return <code>true</code>, if label enrichment may be suppressed. <code>false</code> if suppression of 
	 * label enrichment could create semantic issues (and thus is not permitted).
	 */
	protected abstract boolean isLabelEnrichmentSuppressable();
	
	/**
	 * @return <code>true</code> if label enrichment shall take place, <code>false</code> if label enrichment
	 * is allowed to be suppressed AND is requested to be suppressed.
	 */
	protected boolean isLabelEnrichmentEnabled() {
		if (!this.isLabelEnrichmentSuppressable()) {
			return true;
		}
		
		// we may have label enrichment suppressed
		return this.labelEnrichment;
	}

	private MergableMetricFamilySamples waitForMetricsFetchers(LinkedList<Future<HashMap<String, MetricFamilySamples>>> futures) {
		long starttime = System.currentTimeMillis();
		
		MergableMetricFamilySamples mmfs = new MergableMetricFamilySamples();
		
		for (Future<HashMap<String, MetricFamilySamples>> future : futures) {
			long maxWaitTime = starttime + this.getMaxProcessingTime() - System.currentTimeMillis();
			
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
				log.info("Not all targets could be scraped within the current promregator.scraping.maxProcessingTime. "
						+ "Consider increasing promregator.scraping.maxProcessingTime or promregator.scraping.threads, "
						+ "but mind the implications. See also https://github.com/promregator/promregator/wiki/Handling-Timeouts-on-Scraping");
				continue; // process the other's as well!
			}
			
		}
		return mmfs;
	}

	private long getMaxProcessingTime() {
		if (this.maxProcessingTime != 4000) {
			// different value than the default, so someone must have set it explicitly.
			return this.maxProcessingTime;
		}
		
		if (this.maxProcessingTimeOld.isPresent()) {
			// the deprecated value still is set; use that one
			return this.maxProcessingTimeOld.get();
		}
		
		return this.maxProcessingTime; // must have been the value 4000
	}

	private LinkedList<Future<HashMap<String, MetricFamilySamples>>> startMetricsFetchers(List<MetricsFetcher> callablesPrep) {
		LinkedList<Future<HashMap<String,MetricFamilySamples>>> futures = new LinkedList<>();
		
		for (MetricsFetcher mf : callablesPrep) {
			Future<HashMap<String, MetricFamilySamples>> future = this.metricsFetcherPool.submit(mf);
			
			futures.add(future);
		}
		return futures;
	}

	protected List<MetricsFetcher> createMetricsFetchers(List<Instance> instanceList) {
		
		List<MetricsFetcher> callablesList = new LinkedList<>();
		for (Instance instance : instanceList) {
			log.debug(String.format("Creating Metrics Fetcher for instance %s", instance.getInstanceId()));
			
			ResolvedTarget target = instance.getTarget();
			String orgName = target.getOrgName();
			String spaceName = target.getSpaceName();
			String appName = target.getApplicationName();
			
			String accessURL = instance.getAccessUrl();
			
			if (accessURL == null) {
				log.warn(String.format("Unable to retrieve hostname for %s/%s/%s; skipping", orgName, spaceName, appName));
				continue;
			}
			
			String[] ownTelemetryLabelValues = this.determineOwnTelemetryLabelValues(orgName, spaceName, appName, instance.getInstanceId());
			MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, this.recordRequestLatency);
			
			final boolean labelEnrichmentEnabled = this.isLabelEnrichmentEnabled();
			
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
				
				if (this.proxyHost != null && this.proxyPort != 0) {
					cfmfConfig.setProxyHost(this.proxyHost);
					cfmfConfig.setProxyPort(this.proxyPort);
				}
				mf = new CFMetricsFetcher(accessURL, instance.getInstanceId(), cfmfConfig);
			}
			callablesList.add(mf);
		}
		
		return callablesList;
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
	public boolean isLoopbackRequest() {
		if (this.httpServletRequest == null) {
			log.warn("Missing HTTP Servlet request reference; unable to verify whether this is a lookback request or not");
			return false;
		}
		
		String headerValue = this.httpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER);
		if (headerValue == null) {
			// the header was not set - so this can't be a Promregator instance anyway
			return false;
		}
		
		boolean loopback = this.promregatorInstanceIdentifier.toString().equals(headerValue);
		
		if (loopback) {
			log.error("Errornous loopback request detected. One of your targets is improperly pointing back to Promregator itself. Please revise your configuration!");
		}
		
		return loopback;
	}
}
