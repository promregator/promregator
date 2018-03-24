package org.cloudfoundry.promregator.endpoint;

import java.time.Duration;
import java.time.Instant;
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
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.fetcher.CFMetricsFetcher;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.GenericMetricFamilySamplesPrefixRewriter;
import org.cloudfoundry.promregator.rewrite.MergableMetricFamilySamples;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;

/**
 * An abstract class allowing to easily build a spring-framework HTTP REST-server endpoint, 
 * compliant to the specification of a Prometheus text (!) metrics endpoint,
 * whose data is being backed by a set of further Prometheus metrics endpoints run on one or several CF apps. 
 *
 */
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
public abstract class AbstractMetricsEndpoint {
	
	private static final Logger log = Logger.getLogger(AbstractMetricsEndpoint.class);
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
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
	
	@Autowired
	private AuthenticationEnricher ae;
	
	private GenericMetricFamilySamplesPrefixRewriter gmfspr = new GenericMetricFamilySamplesPrefixRewriter("promregator");
	
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
	
	public String handleRequest() {
		Instant start = Instant.now();
		
		this.up.clear();
		
		List<Instance> instanceList = this.appInstanceScanner.determineInstancesFromTargets(this.promregatorConfiguration.getTargets());

		instanceList = this.filterInstanceList(instanceList);
		
		List<MetricsFetcher> callablesPrep = this.createMetricsFetchers(instanceList);
		
		LinkedList<Future<HashMap<String, MetricFamilySamples>>> futures = this.startMetricsFetchers(callablesPrep);
		
		MergableMetricFamilySamples mmfs = waitForMetricsFetchers(futures);
		
		Instant stop = Instant.now();
		Duration duration = Duration.between(start, stop);
		this.scrape_duration.set(duration.toMillis() / 1000.0);
		
		if (this.isIncludeGlobalMetrics()) {
			// also add our own (global) metrics
			mmfs.merge(this.gmfspr.determineEnumerationOfMetricFamilySamples(this.collectorRegistry));
		}
		
		// add also our own request-specific metrics
		mmfs.merge(this.gmfspr.determineEnumerationOfMetricFamilySamples(this.requestRegistry));
		
		return mmfs.toType004String();
	}

	/**
	 * specifies whether the global metrics provided by Promregator itself
	 * shall be added to the response of the scraping request
	 * @return <code>true</code> if the global metrics shall be added in the response; 
	 * <code>false</code> otherwise.
	 */
	protected abstract boolean isIncludeGlobalMetrics();

	/**
	 * allows to filter the list of detected instances.
	 * Only those instances returned by this method will later be scraped
	 * @return the (reduced) list of instances which shall be scraped
	 */
	protected abstract List<Instance> filterInstanceList(List<Instance> instanceList);

	private MergableMetricFamilySamples waitForMetricsFetchers(LinkedList<Future<HashMap<String, MetricFamilySamples>>> futures) {
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
		return mmfs;
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
			log.info(String.format("Instance %s", instance.getInstanceId()));
			
			Target target = instance.getTarget();
			String orgName = target.getOrgName();
			String spaceName = target.getSpaceName();
			String appName = target.getApplicationName();
			
			String accessURL = instance.getAccessUrl();
			
			if (accessURL == null) {
				log.warn(String.format("Unable to retrieve hostname for %s/%s/%s; skipping", orgName, spaceName, appName));
				continue;
			}
			
			AbstractMetricFamilySamplesEnricher mfse = new CFMetricFamilySamplesEnricher(orgName, spaceName, appName, instance.getInstanceId());
			MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(mfse, up);

			MetricsFetcher mf = null;
			
			if (this.proxyHost != null && this.proxyPort != 0) {
				mf = new CFMetricsFetcher(accessURL, instance.getInstanceId(), this.ae, mfse, this.proxyHost, this.proxyPort, mfm);
			} else {
				mf = new CFMetricsFetcher(accessURL, instance.getInstanceId(), this.ae, mfse, mfm);
			}
			callablesList.add(mf);
		}
		
		return callablesList;
	}
}
