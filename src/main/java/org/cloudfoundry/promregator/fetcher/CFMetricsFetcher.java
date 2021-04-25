package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.UUID;

import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.textformat004.Parser;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger log = LoggerFactory.getLogger(CFMetricsFetcher.class);
	
	private String endpointUrl;
	private String instanceId;
	private boolean withInternalRouting;
	private final RequestConfig config;
	private AuthenticationEnricher ae;
	
	private Gauge.Child up;
	
	private AbstractMetricFamilySamplesEnricher mfse;

	static final CloseableHttpClient httpclient = HttpClients.createSystem();

	private MetricsFetcherMetrics mfm;

	private UUID promregatorUUID;

	/**
	 * creates a new Metrics Fetcher by defining the target endpoint where the metrics can be read, the instance identifier
	 * of the instance, which shall be queried. 
	 * Additional configuration options can be provided using the CFMetricsFetcherConfig reference.
	 * @param endpointUrl the endpoint URL, which shall be used to query the CF app for the Prometheus metrics.
	 * @param instanceId the instance Id in format <i>[app guid]:[instance number]</i>, which identifies the instance uniquely.
	 * @param config additional configurations specifying additional properties for retrieving data.
	 */
	public CFMetricsFetcher(String endpointUrl, String instanceId, CFMetricsFetcherConfig config, boolean withInternalRouting) {
		this.endpointUrl = endpointUrl;
		this.instanceId = instanceId;
		this.ae = config.getAuthenticationEnricher();
		this.mfse = config.getMetricFamilySamplesEnricher();
		this.mfm = config.getMetricsFetcherMetrics();
		this.withInternalRouting = withInternalRouting;

		this.up = config.getUpChild();
		this.promregatorUUID = config.getPromregatorInstanceIdentifier();

		Builder requestConfigBuilder = RequestConfig.custom()
			.setRedirectsEnabled(true)
			.setCircularRedirectsAllowed(false)
			.setMaxRedirects(10)
			.setSocketTimeout(config.getSocketReadTimeoutInMillis())
			.setConnectTimeout(config.getConnectionTimeoutInMillis());
		
		if (config.getProxyHost() != null && config.getProxyPort() != 0) {
			requestConfigBuilder = requestConfigBuilder.setProxy(new HttpHost(config.getProxyHost(), config.getProxyPort(), "http"));
		}
		
		this.config = requestConfigBuilder.build();
	}

	@Override
	public HashMap<String, MetricFamilySamples> call() throws Exception {
		log.debug(String.format("Reading metrics from %s for instance %s", this.endpointUrl, this.instanceId));
		
		HttpGet httpget = setupRequest();

		String result = performRequest(httpget);
		if (result == null) {
			return null;
		}
		
		log.debug(String.format("Successfully received metrics from %s for instance %s", this.endpointUrl, this.instanceId));
		
		if (this.mfm.getRequestSize() != null) {
			this.mfm.getRequestSize().observe(result.length());
		}
		
		Parser parser = new Parser(result);
		HashMap<String, MetricFamilySamples> emfs = parser.parse();
		
		emfs = this.mfse.determineEnumerationOfMetricFamilySamples(emfs);
		
		return emfs;
	}

	private HttpGet setupRequest() {
		HttpGet httpget = new HttpGet(this.endpointUrl);
		
		if (this.config != null) {
			httpget.setConfig(this.config);
		}

		if (!withInternalRouting) {
			// see also https://docs.cloudfoundry.org/concepts/http-routing.html
			httpget.setHeader(HTTP_HEADER_CF_APP_INSTANCE, this.instanceId);
		}

		// provided for recursive scraping / loopback detection
		httpget.setHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER, this.promregatorUUID.toString());
		
		if (this.ae != null) {
			this.ae.enrichWithAuthentication(httpget);
		}
		return httpget;
	}
	
	private String performRequest(HttpGet httpget) {
		CloseableHttpResponse response = null;
		
		Timer timer = null;
		if (this.mfm.getLatencyRequest() != null) {
			timer = this.mfm.getLatencyRequest().startTimer();
		}
		
		boolean available = false;
		
		String result = null;
		try {
			response = httpclient.execute(httpget);

			if (response.getStatusLine().getStatusCode() != 200) {
				log.warn(String.format("Target server at '%s' and instance '%s' responded with a non-200 status code: %d", this.endpointUrl, this.instanceId, response.getStatusLine().getStatusCode()));
				return null;
			}
			
			result = EntityUtils.toString(response.getEntity());
			available = true;
		} catch (HttpHostConnectException hhce) {
			log.warn(String.format("Unable to connect to server trying to fetch metrics from %s, instance %s", this.endpointUrl, this.instanceId), hhce);
			return null;
		} catch (SocketTimeoutException ste) {
			log.warn(String.format("Read timeout for data from socket while trying to fetch metrics from %s, instance %s", this.endpointUrl, this.instanceId), ste);
			return null;
		} catch (ConnectTimeoutException cte) {
			log.warn(String.format("Timeout while trying to connect to %s, instance %s for fetching metrics", this.endpointUrl, this.instanceId), cte);
			return null;
		} catch (ClientProtocolException e) {
			log.warn("Client communication error while fetching metrics from target server", e);
			return null;
		} catch (IOException e) {
			log.warn("IO Exception while fetching metrics from target server", e);
			return null;
		} finally {
			if (timer != null) {
				timer.observeDuration();
			}

			if (response != null) {
				try {
					response.close();
				} catch (IOException e) {
					log.info("Unable to properly close Metrics fetch HTTP connection", e);
					// bad luck!
				}
			}
			
			countSuccessOrFailure(available);
		}
		
		return result;
	}

	private void countSuccessOrFailure(boolean available) {
		if (this.up != null) {
			this.up.set(available ? 1.0 : 0.0);
		}
		
		if (!available && this.mfm.getFailedRequests() != null) {
			this.mfm.getFailedRequests().inc();
		}
	}
}
