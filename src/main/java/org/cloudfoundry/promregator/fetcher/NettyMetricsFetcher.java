package org.cloudfoundry.promregator.fetcher;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;

import io.netty.buffer.ByteBuf;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Histogram.Timer;
import io.prometheus.client.Gauge;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.options.ClientProxyOptions;
import reactor.ipc.netty.options.ClientProxyOptions.Builder;

public class NettyMetricsFetcher implements SynchronousMetricsFetcher {

	private static final Logger log = Logger.getLogger(CFMetricsFetcher.class);

	private String endpointUrl;
	private String instanceId;
	private AuthenticationEnricher ae;

	private Gauge.Child up;

	private AbstractMetricFamilySamplesEnricher mfse;

	private MetricsFetcherMetrics mfm;

	private static final Map<String, HttpClient> httpClientMap = Collections.synchronizedMap(new HashMap<>());
	private HttpClient nettyClient;

	private UUID promregatorUUID;

	public NettyMetricsFetcher(String endpointUrl, String instanceId, AuthenticationEnricher ae,
			@Nullable AbstractMetricFamilySamplesEnricher mfse, String proxyHost, int proxyPort,
			MetricsFetcherMetrics mfm, Gauge.Child up, UUID promregatorUUID) {
		this.endpointUrl = endpointUrl;
		this.instanceId = instanceId;
		this.ae = ae;
		this.mfse = mfse;
		this.mfm = mfm;

		this.up = up;
		this.promregatorUUID = promregatorUUID;

		this.nettyClient = this.determineNettyClient(proxyHost, proxyPort);
	}

	private HttpClient determineNettyClient(String proxyHost, int proxyPort) {
		String proxyKey = (proxyHost == null && proxyPort == 0) ? null : String.format("%s:%d", proxyHost, proxyPort);
		
		synchronized(httpClientMap) {
			HttpClient nettyClient = httpClientMap.get(proxyKey);
			
			if (nettyClient == null) {
				nettyClient = HttpClient.builder().options(options -> {
					if (proxyHost != null && proxyPort != 0) {
						options.proxy(typeSpec -> {
							Builder builder = typeSpec
								.type(ClientProxyOptions.Proxy.HTTP)
								.host(proxyHost)
								.port(proxyPort);
							
							return builder;
						});
					}
				}).build();
				
				httpClientMap.put(proxyKey, nettyClient);
			}
			return nettyClient;
		}
	
	}
	
	@Override
	public HashMap<String, MetricFamilySamples> call() throws Exception {
		log.debug(String.format("Reading metrics from %s for instance %s", this.endpointUrl, this.instanceId));
		
		
		final Timer timer = this.mfm.getLatencyRequest() != null ? this.mfm.getLatencyRequest().startTimer() : null;
		
		boolean available = false;
		String result = null;
		try {
			result = this.nettyClient.get(this.endpointUrl, request -> {
				// see also https://docs.cloudfoundry.org/concepts/http-routing.html
				request.header(MetricsFetcherConstants.HTTP_HEADER_CF_APP_INSTANCE, this.instanceId);
				
				// provided for recursive scraping / loopback detection
				request.header(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER, this.promregatorUUID.toString());
				
				// TODO AEs must be considered here!
				
				return request;
			}).flatMap(resp -> {
				if (resp.status().code() != 200) {
					log.warn(String.format("Target server at '%s' and instance '%s' responded with a non-200 status code: %d", this.endpointUrl, this.instanceId, resp.status().codeAsText()));
					return Mono.empty();
				}
				
				log.debug(String.format("Successfully received metrics from %s for instance %s", this.endpointUrl, this.instanceId));
				return resp.receive().aggregate().asString(Charset.forName("UTF-8"));
			}).doOnSuccessOrError((resp, throwable) -> {
				if (timer != null) {
					timer.observeDuration();
				}
			})
			.block(Duration.ofMillis(2000)); // TODO make this configurable

			if (result == null) {
				// There had been an error before, which was logged
				available = false;
				return null;
			}
			
			available = true;
		} catch (Throwable t) {
			log.warn("Generic Exception raised while fetching metrics from target server", t);
			available = false;
			return null;
		} finally {
			if (timer != null) {
				timer.close();
			}
			
			if (this.up != null) {
				if (available) {
					this.up.set(1.0);
				} else {
					if (this.mfm.getFailedRequests() != null)
						this.mfm.getFailedRequests().inc();
					
					this.up.set(0.0);
				}
			}
		}
		
		if (this.mfm.getRequestSize() != null) {
			this.mfm.getRequestSize().observe(result.length());
		}
		
		TextFormat004Parser parser = new TextFormat004Parser(result);
		HashMap<String, MetricFamilySamples> emfs = parser.parse();

		// we got a proper response
		available = true;
		
		emfs = this.mfse.determineEnumerationOfMetricFamilySamples(emfs);
		
		return emfs;
	}

}
