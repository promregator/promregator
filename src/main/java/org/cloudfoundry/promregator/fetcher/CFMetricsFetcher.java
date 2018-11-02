package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.cloudfoundry.promregator.endpoint.UpMetric;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Histogram.Timer;

/**
 * A MetricsFetcher is a class which retrieves Prometheus metrics at an endpoint URL, which is run
 * by a CF instance. For the sake of retrieving the metrics reliably, it addresses the call to a single CF instance 
 * only using CF Direct HTTP Endpoint Routing.
 * 
 * It implements the MetricsFetcher interface to allow running it in a ThreadPool(Executor). The result of the 
 * Callable is the Prometheus metrics data upon success. In case retrieving the data failed, <code>null</code> is returned.
 *
 */
public class CFMetricsFetcher implements MetricsFetcher {
	
	private static final String HTTP_HEADER_CF_APP_INSTANCE = "X-CF-APP-INSTANCE";

	private static final Logger log = Logger.getLogger(CFMetricsFetcher.class);
	
	private String endpointUrl;
	private String instanceId;
	private final RequestConfig config;
	private AuthenticationEnricher ae;
	
	private UpMetric up;
	
	private AbstractMetricFamilySamplesEnricher mfse;

	final static CloseableHttpClient httpclient = HttpClients.createDefault();

	private MetricsFetcherMetrics mfm;

	private UUID promregatorUUID;

	public CFMetricsFetcher(String endpointUrl, String instanceId, AuthenticationEnricher ae, @Nullable AbstractMetricFamilySamplesEnricher mfse, 
			String proxyHost, int proxyPort, MetricsFetcherMetrics mfm, UpMetric up, UUID promregatorUUID) {
		this.endpointUrl = endpointUrl;
		this.instanceId = instanceId;
		this.ae = ae;
		this.mfse = mfse;
		this.mfm = mfm;
		
		this.up = up;
		this.promregatorUUID = promregatorUUID;

		if (proxyHost != null && proxyPort != 0) {
			this.config = RequestConfig.custom().setProxy(new HttpHost(proxyHost, proxyPort, "http")).build();
		} else {
			this.config = null;
		}
	}

	/**
	 * creates a new Metrics Fetcher by defining the target endpoint where the metrics can be read, the instance identifier
	 * of the instance, which shall be queried. 
	 * Optionally, also an <code>AuthenticationEnricher</code> may be provided to allow setting authentication identifiers 
	 * into the HTTP GET request which is sent by this class.
	 * @param endpointUrl the endpoint URL, which shall be used to query the CF app for the Prometheus metrics.
	 * @param instanceId the instance Id in format <i><app guid>:<instance number></i>, which identifies the instance uniquely.
	 * @param ae (optional) an AuthenticationEnricher, which enriches the HTTP GET request for fetching the Prometheus metrics with additional authentication information.
	 * May be <code>null</code> in which case no enriching takes place.
	 */
	public CFMetricsFetcher(String endpointUrl, String instanceId, AuthenticationEnricher ae, @Nullable AbstractMetricFamilySamplesEnricher mfse, 
			MetricsFetcherMetrics mfm, UpMetric up, UUID promregatorUUID) {
		this(endpointUrl, instanceId, ae, mfse, null, 0, mfm, up, promregatorUUID);
	}

	@Override
	public HashMap<String, MetricFamilySamples> call() throws Exception {
		log.debug(String.format("Reading metrics from %s for instance %s", this.endpointUrl, this.instanceId));
		
		HttpGet httpget = new HttpGet(this.endpointUrl);
		
		if (this.config != null) {
			httpget.setConfig(this.config);
		}

		// see also https://docs.cloudfoundry.org/concepts/http-routing.html
		httpget.setHeader(HTTP_HEADER_CF_APP_INSTANCE, this.instanceId);
		
		// provided for recursive scraping / loopback detection
		httpget.setHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER, this.promregatorUUID.toString());
		
		if (this.ae != null) {
			this.ae.enrichWithAuthentication(httpget);
		}

		CloseableHttpResponse response = null;
		boolean available = false;
		try {
			Timer timer = null;
			if (this.mfm.getLatencyRequest() != null) {
				timer = this.mfm.getLatencyRequest().startTimer();
			}
			
			response = httpclient.execute(httpget);

			if (timer != null) {
				timer.observeDuration();
			}
			
			if (response.getStatusLine().getStatusCode() != 200) {
				log.warn(String.format("Target server at '%s' and instance '%s' responded with a non-200 status code: %d", this.endpointUrl, this.instanceId, response.getStatusLine().getStatusCode()));
				return null;
			}
			log.debug(String.format("Successfully received metrics from %s for instance %s", this.endpointUrl, this.instanceId));
			
			String result = EntityUtils.toString(response.getEntity());
			
			if (this.mfm.getRequestSize() != null) {
				this.mfm.getRequestSize().observe(result.length());
			}
			
			TextFormat004Parser parser = new TextFormat004Parser(result);
			HashMap<String, MetricFamilySamples> emfs = parser.parse();

			// we got a proper response
			available = true;
			
			emfs = this.mfse.determineEnumerationOfMetricFamilySamples(emfs);
			
			return emfs;
		} catch (ClientProtocolException e) {
			log.warn("Client communication error while fetching metrics from target server", e);
			return null;
		} catch (IOException e) {
			log.warn("IO Exception while fetching metrics from target server", e);
			return null;
		} finally {
			if (response != null) {
				try {
					response.close();
				} catch (IOException e) {
					log.info("Unable to properly close Metrics fetch HTTP connection", e);
					// bad luck!
				}
			}
			
			if (this.up != null) {
				if (available) {
					this.up.setUp();
				} else {
					if (this.mfm.getFailedRequests() != null)
						this.mfm.getFailedRequests().inc();
					
					this.up.setDown();
				}
			}
		}
	}
}
