package org.cloudfoundry.promregator.fetcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Random;

import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.endpoint.UpMetric;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Histogram.Timer;

public class MetricsFetcherSimulator implements MetricsFetcher {
	private static final Logger log = Logger.getLogger(MetricsFetcherSimulator.class);
	
	private String accessURL;
	private AuthenticationEnricher ae;
	private AbstractMetricFamilySamplesEnricher mfse;
	private MetricsFetcherMetrics mfm;
	private UpMetric up;
	
	private Random randomLatency = new Random();
	
	private static String SIM_TEXT004;
	
	static {
		InputStream is = MetricsFetcherSimulator.class.getResourceAsStream("simulation.text004");
		
		assert is != null;
		
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		try {
			while ((length = is.read(buffer)) != -1) {
				result.write(buffer, 0, length);
			}
			SIM_TEXT004 = result.toString("UTF-8");
		} catch (IOException e) {
			SIM_TEXT004 = "";
		} finally {
			try {
				result.close();
			} catch (IOException e) {
				log.warn(String.format("Unable to close result ByteArrayOutputStream having read the simulation data"));
			}
			
			try {
				is.close();
			} catch (IOException e) {
				log.warn(String.format("Unable to close the input stream while reading the simulation data"));
			}
		}
		
	}
	
	public MetricsFetcherSimulator(String accessURL, AuthenticationEnricher ae,
			AbstractMetricFamilySamplesEnricher mfse, MetricsFetcherMetrics mfm, UpMetric up) {
				this.accessURL = accessURL;
				this.ae = ae;
				this.mfse = mfse;
				this.mfm = mfm;
				this.up = up;
		
	}

	@Override
	public HashMap<String, MetricFamilySamples> call() throws Exception {
		Timer timer = null;
		if (this.mfm.getLatencyRequest() != null) {
			timer = this.mfm.getLatencyRequest().startTimer();
		}
		
		HttpGet httpget = new HttpGet(this.accessURL);
		
		if (this.ae != null) {
			this.ae.enrichWithAuthentication(httpget);
		}
		
		String result = SIM_TEXT004;
		
		TextFormat004Parser parser = new TextFormat004Parser(result);
		HashMap<String, MetricFamilySamples> emfs = parser.parse();
		
		emfs = this.mfse.determineEnumerationOfMetricFamilySamples(emfs);
		
		int latency = this.randomLatency.nextInt(300);
		
		log.info(String.format("Simulating scraping at %s with latency of %d ms", this.accessURL, latency));
		Thread.sleep(latency);
		
		this.up.setUp();
		
		if (timer != null) {
			timer.observeDuration();
		}
		
		return emfs;
	}

}
