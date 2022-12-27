package org.cloudfoundry.promregator.fetcher;

import java.util.UUID;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;

import io.prometheus.client.Gauge;

public class CFMetricsFetcherConfig {
	private AuthenticationEnricher authenticationEnricher;
	private MetricsFetcherMetrics metricsFetcherMetrics;
	
	private String proxyHost;
	private int proxyPort;
	
	private Gauge.Child upChild;
	
	private UUID promregatorInstanceIdentifier;
	
	private int connectionTimeoutInMillis;
	private int socketReadTimeoutInMillis;
	
	public CFMetricsFetcherConfig() {
		super();
	}

	/**
	 * @return the authenticationEnricher
	 */
	public AuthenticationEnricher getAuthenticationEnricher() {
		return authenticationEnricher;
	}

	/**
	 * @param authenticationEnricher the authenticationEnricher to set
	 */
	public void setAuthenticationEnricher(AuthenticationEnricher authenticationEnricher) {
		this.authenticationEnricher = authenticationEnricher;
	}

	/**
	 * @return the metricsFetcherMetrics
	 */
	public MetricsFetcherMetrics getMetricsFetcherMetrics() {
		return metricsFetcherMetrics;
	}

	/**
	 * @param metricsFetcherMetrics the metricsFetcherMetrics to set
	 */
	public void setMetricsFetcherMetrics(MetricsFetcherMetrics metricsFetcherMetrics) {
		this.metricsFetcherMetrics = metricsFetcherMetrics;
	}

	/**
	 * @return the proxyHost
	 */
	public String getProxyHost() {
		return proxyHost;
	}

	/**
	 * @param proxyHost the proxyHost to set
	 */
	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	/**
	 * @return the proxyPort
	 */
	public int getProxyPort() {
		return proxyPort;
	}

	/**
	 * @param proxyPort the proxyPort to set
	 */
	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	/**
	 * @return the upChild
	 */
	public Gauge.Child getUpChild() {
		return upChild;
	}

	/**
	 * @param upChild the upChild to set
	 */
	public void setUpChild(Gauge.Child upChild) {
		this.upChild = upChild;
	}

	/**
	 * @return the promregatorInstanceIdentifier
	 */
	public UUID getPromregatorInstanceIdentifier() {
		return promregatorInstanceIdentifier;
	}

	/**
	 * @param promregatorInstanceIdentifier the promregatorInstanceIdentifier to set
	 */
	public void setPromregatorInstanceIdentifier(UUID promregatorInstanceIdentifier) {
		this.promregatorInstanceIdentifier = promregatorInstanceIdentifier;
	}

	/**
	 * @return the connectionTimeoutInMillis
	 */
	public int getConnectionTimeoutInMillis() {
		return connectionTimeoutInMillis;
	}

	/**
	 * @param connectionTimeoutInMillis the connectionTimeoutInMillis to set
	 */
	public void setConnectionTimeoutInMillis(int connectionTimeoutInMillis) {
		this.connectionTimeoutInMillis = connectionTimeoutInMillis;
	}

	/**
	 * @return the socketReadTimeoutInMillis
	 */
	public int getSocketReadTimeoutInMillis() {
		return socketReadTimeoutInMillis;
	}

	/**
	 * @param socketReadTimeoutInMillis the socketReadTimeoutInMillis to set
	 */
	public void setSocketReadTimeoutInMillis(int socketReadTimeoutInMillis) {
		this.socketReadTimeoutInMillis = socketReadTimeoutInMillis;
	}
	
	
}
