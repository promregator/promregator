package org.cloudfoundry.promregator.fetcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram.Timer;
import io.prometheus.client.exporter.common.TextFormat;

public class MetricsFetcherSimulator implements MetricsFetcher {
	private static final Logger log = LoggerFactory.getLogger(MetricsFetcherSimulator.class);
	
	private String accessURL;
	private AuthenticationEnricher ae;
	private MetricsFetcherMetrics mfm;
	private Gauge.Child up;
	
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
			SIM_TEXT004 = result.toString(StandardCharsets.UTF_8.name());
		} catch (IOException e) {
			SIM_TEXT004 = "";
		} finally {
			try {
				result.close();
			} catch (IOException e) {
				log.warn("Unable to close result ByteArrayOutputStream having read the simulation data");
			}
			
			try {
				is.close();
			} catch (IOException e) {
				log.warn("Unable to close the input stream while reading the simulation data");
			}
		}
		
	}
	
	public MetricsFetcherSimulator(String accessURL, AuthenticationEnricher ae,
			MetricsFetcherMetrics mfm, Gauge.Child up) {
				this.accessURL = accessURL;
				this.ae = ae;
				this.mfm = mfm;
				this.up = up;
		
	}

	@Override
	public FetchResult call() throws Exception {
		Timer timer = null;
		if (this.mfm.getLatencyRequest() != null) {
			timer = this.mfm.getLatencyRequest().startTimer();
		}
		
		HttpGet httpget = new HttpGet(this.accessURL);
		
		if (this.ae != null) {
			this.ae.enrichWithAuthentication(httpget);
		}
		
		int latency = this.randomLatency.nextInt(300);
		
		log.info("Simulating scraping at {} with latency of {} ms", this.accessURL, latency);
		Thread.sleep(latency);
		
		this.up.set(1.0);
		
		if (timer != null) {
			timer.observeDuration();
		}
		
		return new FetchResult(SIM_TEXT004, TextFormat.CONTENT_TYPE_004);
	}

}
