package org.cloudfoundry.promregator.endpoint;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
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
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.GenericMetricFamilySamplesPrefixRewriter;
import org.cloudfoundry.promregator.rewrite.MFSUtils;
import org.cloudfoundry.promregator.rewrite.MergableMetricFamilySamples;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
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
public class MetricsEndpoint {
	
	private static final Logger log = Logger.getLogger(MetricsEndpoint.class);
	
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
	
	private AuthenticationEnricher ae;
	private GenericMetricFamilySamplesPrefixRewriter gmfspr = new GenericMetricFamilySamplesPrefixRewriter("promregator");
	
	/* own metrics */
	private static Histogram requestLatency = Histogram.build("promregator_request_latency", "The latency, which the targets of the promregator produce")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	
	private static Gauge up = Gauge.build("promregator_up", "Indicator, whether the target of promregator is available")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	
	private static Gauge failedRequests = Gauge.build("promregator_request_failure", "Requests, which responded, but the HTTP code indicated an error or the connection dropped/timed out")
			.labelNames(CFMetricFamilySamplesEnricher.getEnrichingLabelNames())
			.register();
	
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
				if (maxWaitTime < 0) {
					if (!future.isDone()) {
						// only process those, which are already completed
						continue;
					}
				}
				HashMap<String, MetricFamilySamples> emfs = future.get(maxWaitTime, TimeUnit.MILLISECONDS);
				mmfs.merge(emfs);
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

		// also add our own metrics
		Enumeration<MetricFamilySamples> rawMFS = this.collectorRegistry.metricFamilySamples();
		HashMap<String, MetricFamilySamples> enrichedMFS = this.gmfspr.determineEnumerationOfMetricFamilySamples(MFSUtils.convertToEMFSToHashMap(rawMFS));
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
		List<MetricsFetcher> callablesPrep = new LinkedList<MetricsFetcher>();
		for (Target target : this.promregatorConfiguration.getTargets()) {
			String orgName = target.getOrgName();
			String spaceName = target.getSpaceName();
			String appName = target.getApplicationName();
			
			String hostname = this.appInstanceScanner.getFirstHostname(orgName, spaceName, appName);
			if (hostname == null) {
				log.warn(String.format("Unable to retrieve hostname for %s/%s/%s; skipping", orgName, spaceName, appName));
				continue;
			}
			String accessURL = String.format("https://%s%s", hostname, target.getPath());
			
			Set<String> instances = this.appInstanceScanner.getInstanceIds(orgName, spaceName, appName);
			if (instances == null) {
				log.warn(String.format("Unable to retrieve instances for %s/%s/%s; skipping", orgName, spaceName, appName));
				continue;
			}
			
			log.info(String.format("Seeing the following instances for org '%s', space '%s', app '%s' for access URL '%s':", orgName, spaceName, appName, accessURL));
			
			for (String instance: instances) {
				log.info(String.format("Instance %s", instance));
				
				AbstractMetricFamilySamplesEnricher mfse = new CFMetricFamilySamplesEnricher(orgName, spaceName, appName, instance);
				
				String[] labelNamesForOwnMetrics = { orgName, spaceName, appName, instance, CFMetricFamilySamplesEnricher.getInstanceFromInstanceId(instance) };
				
				MetricsFetcher mf = null;
				if (this.proxyHost != null && this.proxyPort != 0) {
					mf = new MetricsFetcher(accessURL, instance, this.ae, mfse, this.proxyHost, this.proxyPort, labelNamesForOwnMetrics, requestLatency, up, failedRequests);
				} else {
					mf = new MetricsFetcher(accessURL, instance, this.ae, mfse, labelNamesForOwnMetrics, requestLatency, up, failedRequests);
				}
				callablesPrep.add(mf);
			}
		}
		return callablesPrep;
	}
}
