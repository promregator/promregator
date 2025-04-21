package org.cloudfoundry.promregator.fetcher;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram.Timer;
import io.prometheus.client.exporter.common.TextFormat;

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
	private static final Logger logWrongVersion = LoggerFactory.getLogger(CFMetricsFetcher.class.toString()+".wrongVersion");
	
	private static final Pattern CONTENT_TYPE_OPENMETRIC_100 = Pattern.compile("^application/openmetrics-text; *version=1\\.\\d++\\.\\d++; *charset=utf-8");
	private static final Pattern CONTENT_TYPE_OPENMETRIC_100_WRONG_VERSION = Pattern.compile("^application/openmetrics-text; *version=(0\\.\\d++\\.\\d++)");
	private static final Pattern CONTENT_TYPE_TEXT_004 = Pattern.compile("^text/plain; *version=0\\.0\\.4; *charset=utf-8");
	
	private String endpointUrl;
	private String instanceId;
	private boolean withInternalRouting;
	private final RequestConfig config;
	private AuthenticationEnricher ae;
	
	private Gauge.Child up;
	
	static final CloseableHttpClient globalHttpclient = HttpClients.createSystem();
	
	private CloseableHttpClient localHttpClient;
	
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
	public FetchResult call() throws Exception {
		log.debug("Reading metrics from {} for instance {}", this.endpointUrl, this.instanceId);
		
		HttpGet httpget = setupRequest();

		FetchResult result = performRequest(httpget);
		if (result == null) {
			return null;
		}
		
		log.debug("Successfully received metrics from {} for instance {}", this.endpointUrl, this.instanceId);
		
		if (this.mfm.getRequestSize() != null) {
			this.mfm.getRequestSize().observe(result.data().length());
		}
		
		return result;
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
		
		httpget.setHeader(HttpHeaders.ACCEPT, "%s, %s;q=0.9".formatted(TextFormat.CONTENT_TYPE_OPENMETRICS_100, TextFormat.CONTENT_TYPE_004));
		
		if (this.ae != null) {
			this.ae.enrichWithAuthentication(httpget);
		}
		return httpget;
	}
	
	private FetchResult performRequest(HttpGet httpget) {
		CloseableHttpResponse response = null;
		
		Timer timer = null;
		if (this.mfm.getLatencyRequest() != null) {
			timer = this.mfm.getLatencyRequest().startTimer();
		}
		
		boolean available = false;
		
		FetchResult result = null;
		try {
			@SuppressWarnings("resource") // there is no closing necessary here - we are just choosing the "right" client here.
			final CloseableHttpClient httpClient = this.localHttpClient != null ? this.localHttpClient : globalHttpclient;
			response = httpClient.execute(httpget);

			if (response.getStatusLine().getStatusCode() != 200) {
				log.warn("Target server at '{}' and instance '{}' responded with a non-200 status code: {}", this.endpointUrl, this.instanceId, response.getStatusLine().getStatusCode());
				return null;
			}
			
			final Header contentTypeHeader = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
			final String contentType = this.determineTextFormat(contentTypeHeader);
			if (contentType == null) {
				return null;
			}
			
			result = new FetchResult(EntityUtils.toString(response.getEntity()), contentType);
			available = true;
		} catch (HttpHostConnectException hhce) {
			log.warn("Unable to connect to server trying to fetch metrics from {}, instance {}", this.endpointUrl, this.instanceId, hhce);
			return null;
		} catch (SocketTimeoutException ste) {
			log.warn("Read timeout for data from socket while trying to fetch metrics from {}, instance {}", this.endpointUrl, this.instanceId, ste);
			return null;
		} catch (ConnectTimeoutException cte) {
			log.warn("Timeout while trying to connect to {}, instance {} for fetching metrics", this.endpointUrl, this.instanceId, cte);
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

	private String determineTextFormat(Header contentTypeHeader) {
		if (contentTypeHeader == null) {
			return TextFormat.CONTENT_TYPE_004;
		}
		
		final String contentTypeValue = contentTypeHeader.getValue();
		if (contentTypeValue == null) {
			return TextFormat.CONTENT_TYPE_004;
		}
		
		final Matcher matcherOpenMetric100 = CONTENT_TYPE_OPENMETRIC_100.matcher(contentTypeValue);
		if (matcherOpenMetric100.find()) {
			return TextFormat.CONTENT_TYPE_OPENMETRICS_100;
		}
		
		final Matcher matcherOpenMetric100WrongVersion = CONTENT_TYPE_OPENMETRIC_100_WRONG_VERSION.matcher(contentTypeValue);
		if (matcherOpenMetric100WrongVersion.find()) {
			if (logWrongVersion.isWarnEnabled()) {
				final String versionIdentifierProvided = matcherOpenMetric100WrongVersion.group(1);
				logWrongVersion.warn("The implementation at {} and instance {} returned an invalid content type by specifying an invalid version identifier {} of the OpenMetric format. "
						+ "Promregator is guessing that you mean version 1.0.0. Please fix your Prometheus client library! "
						+ "See also https://github.com/promregator/promregator/wiki/Invalid-Version-In-Content-Type-of-OpenMetrics-Endpoints", 
						this.endpointUrl, this.instanceId, versionIdentifierProvided, logWrongVersion.getName());
			}
			return TextFormat.CONTENT_TYPE_OPENMETRICS_100;
		}
		
		final Matcher matcherText004 = CONTENT_TYPE_TEXT_004.matcher(contentTypeValue);
		if (matcherText004.find()) {
			return TextFormat.CONTENT_TYPE_004;
		}
		
		log.warn("Target at endpoint URL {} and instance {} returned a Content-Type header on scraping which is unknown by Promregator: {}", this.endpointUrl, this.instanceId, contentTypeValue);
		return null;
	}

	private void countSuccessOrFailure(boolean available) {
		if (this.up != null) {
			this.up.set(available ? 1.0 : 0.0);
		}
		
		if (!available && this.mfm.getFailedRequests() != null) {
			this.mfm.getFailedRequests().inc();
		}
	}
	

	/**
	 * shall only be used by unit tests!
	 * @param localHttpClient the localHttpClient to set
	 */
	protected void setLocalHttpClient(CloseableHttpClient localHttpClient) {
		this.localHttpClient = localHttpClient;
	}

}
