package org.cloudfoundry.promregator.endpoint;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.fetcher.CFMetricsFetcher;
import org.cloudfoundry.promregator.fetcher.MetricsFetcher;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.MergableMetricFamilySamples;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.WebApplicationContext;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.exporter.common.TextFormat;

@RestController
@RequestMapping(SingleTargetMetricsEndpoint.ENDPOINT_PATH+"/{applicationId}/{instanceNumber}")
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
public class SingleTargetMetricsEndpoint {
	public static final String ENDPOINT_PATH = "/singleTargetMetrics";
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;
	
	@Autowired
	private AuthenticationEnricher ae;

	@Value("${cf.proxyHost:@null}")
	private String proxyHost;
	
	@Value("${cf.proxyPort:0}")
	private int proxyPort;
	
	@Value("${promregator.endpoint.maxProcessingTime:5000}")
	private int maxProcessingTime;
	
	@Autowired
	private ExecutorService metricsFetcherPool;
	
	@RequestMapping(method = RequestMethod.GET, produces=TextFormat.CONTENT_TYPE_004)
	public String getMetrics(
			@PathVariable String applicationId, 
			@PathVariable String instanceNumber
			) {
		
		String instanceId = String.format("%s:%s", applicationId, instanceNumber);
		
		List<Instance> instanceList = this.appInstanceScanner.determineInstancesFromTargets(this.promregatorConfiguration.getTargets());

		Instance selectedInstance = null;
		for (Instance instance : instanceList) {
			if (instance.getInstanceId().equals(instanceId)) {
				selectedInstance = instance;
				break;
			}
		}
		
		if (selectedInstance == null) {
			throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
		}
		
		Target target = selectedInstance.getTarget();
		String orgName = target.getOrgName();
		String spaceName = target.getSpaceName();
		String appName = target.getApplicationName();

		String accessURL = selectedInstance.getAccessUrl();

		AbstractMetricFamilySamplesEnricher mfse = new CFMetricFamilySamplesEnricher(orgName, spaceName, appName, selectedInstance.getInstanceId());
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(mfse, null, null, null);
		
		MetricsFetcher mf = null;
		if (this.proxyHost != null && this.proxyPort != 0) {
			mf = new CFMetricsFetcher(accessURL, selectedInstance.getInstanceId(), this.ae, mfse, this.proxyHost, this.proxyPort, mfm);
		} else {
			mf = new CFMetricsFetcher(accessURL, selectedInstance.getInstanceId(), this.ae, mfse, mfm);
		}
		
		Future<HashMap<String, MetricFamilySamples>> future = this.metricsFetcherPool.submit(mf);
		
		HashMap<String, MetricFamilySamples> resultMap = null;
		try {
			resultMap = future.get(this.maxProcessingTime, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// TODO logging
			throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
		} catch (ExecutionException e) {
			// TODO logging
			throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
		} catch (TimeoutException e) {
			// TODO logging
			throw new HttpClientErrorException(HttpStatus.BAD_GATEWAY);
		}
		
		MergableMetricFamilySamples mmfs = new MergableMetricFamilySamples();
		mmfs.merge(resultMap);
		
		return mmfs.toType004String();
	}

	
}
