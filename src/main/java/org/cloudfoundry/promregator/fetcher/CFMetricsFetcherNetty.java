package org.cloudfoundry.promregator.fetcher;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.textformat004.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram.Timer;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.ProxyProvider;
import reactor.netty.tcp.TcpClient;

public class CFMetricsFetcherNetty implements MetricsFetcher {
	
	private static final String HTTP_HEADER_CF_APP_INSTANCE = "X-CF-APP-INSTANCE";

	private static final Logger log = LoggerFactory.getLogger(CFMetricsFetcherNetty.class);
	
	private String endpointUrl;
	private String instanceId;
	private final CFMetricsFetcherConfig config;

	private Gauge.Child up;
	private MetricsFetcherMetrics mfm;
	private Timer requestTimer;
	
	private AbstractMetricFamilySamplesEnricher mfse;

	public CFMetricsFetcherNetty(String endpointUrl, String instanceId, CFMetricsFetcherConfig config) {
		this.endpointUrl = endpointUrl;
		this.instanceId = instanceId;
		this.config = config;
		
		this.up = config.getUpChild();
		this.mfm = config.getMetricsFetcherMetrics();
		
		this.mfse = config.getMetricFamilySamplesEnricher();
	}

	@Override
	public HashMap<String, MetricFamilySamples> call() throws Exception {
		Mono<String> text004String = createNettyRequest();
		
		Mono<HashMap<String, MetricFamilySamples>> emfs = text004String.map(raw -> {
			Parser p = new Parser(raw);
			HashMap<String, MetricFamilySamples> emfsRaw = p.parse();
			
			return this.mfse.determineEnumerationOfMetricFamilySamples(emfsRaw);
		});
		
		return emfs.block();
	}

	private static class InvalidStatusCodeFromClient extends RuntimeException {

		private static final long serialVersionUID = 1527528376120796948L;

		public InvalidStatusCodeFromClient(String message) {
			super(message);
		}
		
	}
	
	private Mono<String> createNettyRequest() {
		// see also https://projectreactor.io/docs/netty/release/reference/index.html#_connect
		log.debug(String.format("Reading metrics from %s for instance %s", this.endpointUrl, this.instanceId));
		
		Mono<String> response = HttpClient.create()
			.compress(true)
			.followRedirect(true)
			.tcpConfiguration(this::getTCPConfiguration)
			.headers(this::getRequestHeaders)
			.doAfterRequest((req, conn) -> {
				if (this.mfm.getLatencyRequest() != null) {
					this.requestTimer = this.mfm.getLatencyRequest().startTimer();
				}
			})
			.doAfterResponse((res, conn) -> {
				if (this.requestTimer != null) {
					this.requestTimer.observeDuration();
				}
			})
			.get()
			.uri(this.endpointUrl)
			.responseSingle((headers, content) -> {
				if (headers.status() == HttpResponseStatus.OK) {
					return content.asString(StandardCharsets.UTF_8);
				}
				
				log.warn(String.format("Target server at '%s' and instance '%s' responded with a non-200 status code: %d", this.endpointUrl, this.instanceId, headers.status().code()));
				throw new InvalidStatusCodeFromClient(String.format("Invalid HTTP Status code from %s: %d", this.instanceId, headers.status().code()));
			})
			.doOnError(err -> {
				log.warn(String.format("Unable to retrieve metrics from %s, instance %s",  this.endpointUrl, this.instanceId), err);
				
				this.countSuccessOrFailure(false);
			})
			.doOnNext(text004String -> {
				log.debug(String.format("Successfully received metrics from %s for instance %s", this.endpointUrl, this.instanceId));
				
				this.countSuccessOrFailure(true);
				
				if (this.mfm.getRequestSize() != null) {
					this.mfm.getRequestSize().observe(text004String.length());
				}
			});
		
		return response;
	}

	private TcpClient getTCPConfiguration(TcpClient tcpClient) {
		tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.config.getConnectionTimeoutInMillis());
		tcpClient.option(ChannelOption.SO_TIMEOUT, this.config.getSocketReadTimeoutInMillis());
		
		if (this.config.getProxyHost() != null && this.config.getProxyPort() != 0) {
			tcpClient.proxy(spec -> spec.type(ProxyProvider.Proxy.HTTP)
					.host(this.config.getProxyHost())
					.port(this.config.getProxyPort()));
		}
		return tcpClient;

	}
	
	private HttpHeaders getRequestHeaders(HttpHeaders headers) {
		// see also https://docs.cloudfoundry.org/concepts/http-routing.html
		headers.set(HTTP_HEADER_CF_APP_INSTANCE, this.instanceId);
		
		// provided for recursive scraping / loopback detection
		headers.set(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER, this.config.getPromregatorInstanceIdentifier().toString());
		
		// TODO AE is still missing here!
		
		return headers;
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
