package org.cloudfoundry.promregator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.springconfig.CFClientSpringConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;

@SpringBootApplication
@Import({ CFClientSpringConfiguration.class })
public class PromregatorApplication {
	
	private static final Logger log = Logger.getLogger(PromregatorApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(PromregatorApplication.class, args);
	}
	
	@Bean
	public ReactiveAppInstanceScanner reactiveAppInstanceScanner() {
		return new ReactiveAppInstanceScanner();
	}
	
	@Bean
	public CollectorRegistry collectorRegistry() {
		CollectorRegistry cr = CollectorRegistry.defaultRegistry;
		
		DefaultExports.initialize();
		
		return cr;
	}
	
	@Bean
	public InternalMetrics internalMetrics() {
		return new InternalMetrics();
	}
	
	@Value("${promregator.endpoint.threads:5}")
	private int threadPoolSize;
	
	@Bean
	public ExecutorService metricsFetcherPool() {
		log.info(String.format("Thread Pool size is set to %d", this.threadPoolSize));
		return Executors.newFixedThreadPool(this.threadPoolSize);
	}
}
