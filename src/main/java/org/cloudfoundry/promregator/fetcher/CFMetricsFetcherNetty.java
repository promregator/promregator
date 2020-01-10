package org.cloudfoundry.promregator.fetcher;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.textformat004.Parser;

import io.netty.channel.ChannelOption;
import io.prometheus.client.Collector.MetricFamilySamples;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.ProxyProvider;
import reactor.netty.tcp.TcpClient;

public class CFMetricsFetcherNetty implements MetricsFetcher {
	
	private static final String HTTP_HEADER_CF_APP_INSTANCE = "X-CF-APP-INSTANCE";

	private String endpointUrl;
	private String instanceId;
	private final CFMetricsFetcherConfig config;

	private AbstractMetricFamilySamplesEnricher mfse;

	public CFMetricsFetcherNetty(String endpointUrl, String instanceId, CFMetricsFetcherConfig config) {
		this.endpointUrl = endpointUrl;
		this.instanceId = instanceId;
		this.config = config;
		
		this.mfse = config.getMetricFamilySamplesEnricher();
	}

	@Override
	public HashMap<String, MetricFamilySamples> call() throws Exception {
		ByteBufFlux response = HttpClient.create()
			.compress(true)
			.followRedirect(true)
			.tcpConfiguration(this::getTCPConfiguration)
			.headers(headers -> {
				// see also https://docs.cloudfoundry.org/concepts/http-routing.html
				headers.set(HTTP_HEADER_CF_APP_INSTANCE, this.instanceId);
				
				// provided for recursive scraping / loopback detection
				headers.set(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER, this.config.getPromregatorInstanceIdentifier().toString());
				
				// TODO AE is still missing here!
			})
			.get()
			.uri(this.endpointUrl)
			.responseContent();
		
		Mono<String> text004String = response.aggregate().asString(StandardCharsets.UTF_8);
		
		Mono<HashMap<String, MetricFamilySamples>> emfs = text004String.map(raw -> {
			Parser p = new Parser(raw);
			HashMap<String, MetricFamilySamples> emfsRaw = p.parse();
			
			return this.mfse.determineEnumerationOfMetricFamilySamples(emfsRaw);
		});
		
		// TODO: Handling up still missing!
		
		// TODO MetricsFetcherMetrics still missing!
		
		return emfs.block();
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

}
