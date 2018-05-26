package org.cloudfoundry.promregator;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.BasicAuthenticationEnricher;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.auth.OAuth2XSUAAEnricher;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorSimulator;
import org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.lifecycle.InstanceLifecycleHandler;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveTargetResolver;
import org.cloudfoundry.promregator.scanner.CachingTargetResolver;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.cloudfoundry.promregator.springconfig.BasicAuthenticationSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.ErrorSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.cloudfoundry.promregator.websecurity.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableScheduling
@Import({ BasicAuthenticationSpringConfiguration.class, SecurityConfig.class, ErrorSpringConfiguration.class, JMSSpringConfiguration.class })
@EnableAsync
public class PromregatorApplication {
	
	@Value("${promregator.simulation.enabled:false}")
	private boolean simulationMode;

	@Value("${promregator.simulation.instances:10}")
	private int simulationInstances;
	
	@Value("${promregator.reactor.debug:false}")
	private boolean reactorDebugEnabled;
	
	private static final Logger log = Logger.getLogger(PromregatorApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(PromregatorApplication.class, args);
	}
	
	@PostConstruct
	public void enableReactorDebugging() {
		if (this.reactorDebugEnabled) {
			Hooks.onOperatorDebug();
		}
	}
	
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
	
	@Bean
	public CFAccessor cfAccessor() throws ConfigurationException {
		if (this.simulationMode) {
			return new CFAccessorSimulator(this.simulationInstances);
		}
		return new ReactiveCFAccessorImpl();
	}
	
	@Bean
	public ReactiveTargetResolver reactiveTargetResolver() {
		return new ReactiveTargetResolver();
	}
	
	@Bean
	public CachingTargetResolver cachingTargetResolver(ReactiveTargetResolver reactiveTargetResolver) {
		return new CachingTargetResolver(reactiveTargetResolver);
	}
	
	@Bean
	public TargetResolver targetResolver(CachingTargetResolver cachingTargetResolver) {
		return cachingTargetResolver;
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new ReactiveAppInstanceScanner();
	}
	
	@Bean
	public CFDiscoverer cfDiscoverer() {
		return new CFDiscoverer();
	}
	
	@Bean
	public InstanceLifecycleHandler instanceLifecycleHandler() {
		return new InstanceLifecycleHandler();
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
	
	/* see also https://github.com/promregator/promregator/issues/54 */
	@Scheduled(fixedRateString = "${promregator.gc.rate:1200}000")
	@SuppressFBWarnings(value="DM_GC", justification="Similar situation as with RMI, which uses this approach")
	public void forceGC() {
		log.info("Triggering major garbage collection");
		System.gc();
	}
}
