package org.cloudfoundry.promregator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.BasicAuthenticationEnricher;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.auth.OAuth2XSUAAEnricher;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.springconfig.BasicAuthenticationSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.ErrorSpringConfiguration;
import org.cloudfoundry.promregator.websecurity.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;

@SpringBootApplication
@Import({ BasicAuthenticationSpringConfiguration.class, SecurityConfig.class, ErrorSpringConfiguration.class })
public class PromregatorApplication {
	
	private static final Logger log = Logger.getLogger(PromregatorApplication.class);

	public static final String SPACE_ALL_APPLICATIONS = "SPACE_ALL_APPLICATIONS";
	
	public static void main(String[] args) {
		SpringApplication.run(PromregatorApplication.class, args);
	}
	
	@Bean
	public CFAccessor cfAccessor() throws ConfigurationException {
		return new ReactiveCFAccessorImpl();
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
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
	
	@Bean
	public AuthenticationEnricher authenticationEnricher(PromregatorConfiguration promregatorConfiguration) {
		AuthenticationEnricher ae = null;
		
		String type = promregatorConfiguration.getAuthenticator().getType();
		if ("OAuth2XSUAA".equalsIgnoreCase(type)) {
			ae = new OAuth2XSUAAEnricher(promregatorConfiguration.getAuthenticator().getOauth2xsuaa());
		} else if ("none".equalsIgnoreCase(type) || "null".equalsIgnoreCase(type)) {
			ae = new NullEnricher();
		} else if ("basic".equalsIgnoreCase(type)) {
			ae = new BasicAuthenticationEnricher(promregatorConfiguration.getAuthenticator().getBasic());
		} else {
			log.warn(String.format("Authenticator type %s is unknown; skipping", type));
		}

		return ae;
	}
}
