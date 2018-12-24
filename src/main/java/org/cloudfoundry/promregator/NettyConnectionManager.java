package org.cloudfoundry.promregator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.options.ClientProxyOptions;
import reactor.ipc.netty.options.ClientProxyOptions.Builder;

public class NettyConnectionManager {
	private static final Map<String, HttpClient> httpClientMap = Collections.synchronizedMap(new HashMap<>());
	
	public static HttpClient determineNettyClient(String proxyHost, int proxyPort) {
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
}
