package org.cloudfoundry.promregator.fetcher;

import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CFMetricsFetcherConnManager {
	private static final Logger log = LoggerFactory.getLogger(CFMetricsFetcherConnManager.class);
	
	private PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
	
	private CloseableHttpClient httpclient;

	public CFMetricsFetcherConnManager(int socketTimeoutMillis, long connectionTimeoutMillis, long scrapingMaxProcessingTime, String proxyHost, int proxyPort) {
		final long localMaxProcessingTime = scrapingMaxProcessingTime;
		
		if (connectionTimeoutMillis > localMaxProcessingTime) {
			log.warn("Fetcher's Connection Timeout is longer than the configured Maximal Processing Time of all fetchers; shortening timeout value to that value, as this does not make sense. "+
					"Check your configured values for configuration options promregator.scraping.connectionTimeout and promregator.scraping.maxProcessingTime");
			connectionTimeoutMillis = (int) localMaxProcessingTime;
		}
		
		if (socketTimeoutMillis > localMaxProcessingTime) {
			log.warn("Fetcher's Socket Read Timeout is longer than the configured Maximal Processing Time of all fetchers; shortening timeout value to that value, as this does not make sense. "+
					"Check your configured values for configuration options promregator.scraping.socketReadTimeout and promregator.scraping.maxProcessingTime");
			socketTimeoutMillis = (int) localMaxProcessingTime;
		}

		
		final SocketConfig socketConfig = SocketConfig.custom()
				.setSoKeepAlive(true)
				.setSoReuseAddress(true)
				.build();
		
		this.connManager.setDefaultSocketConfig(socketConfig);
		
		final ConnectionConfig connectionConfig = ConnectionConfig.custom()
				.setSocketTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
				.setConnectTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS)
				.build();
		this.connManager.setDefaultConnectionConfig(connectionConfig);
	
		final HttpClientBuilder httpClientBuilder = HttpClients.custom()
				.setConnectionManager(this.connManager);

		if (proxyHost != null && proxyPort != 0) {
			httpClientBuilder.setRoutePlanner(new DefaultProxyRoutePlanner(new HttpHost("http", proxyHost, proxyPort)));
		}
		
		this.httpclient = httpClientBuilder.build();
	}

	/**
	 * @return the httpclient
	 */
	public CloseableHttpClient getHttpclient() {
		return httpclient;
	}
	
}
