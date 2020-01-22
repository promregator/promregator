package org.cloudfoundry.promregator;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorCache;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorCacheCaffeine;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorSimulator;
import org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl;
import org.cloudfoundry.promregator.config.ConfigurationValidations;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.endpoint.InstanceCache;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.lifecycle.InstanceLifecycleHandler;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.CachingTargetResolver;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveTargetResolver;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.cloudfoundry.promregator.springconfig.AuthenticatorSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.BasicAuthenticationSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.ErrorSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.cloudfoundry.promregator.websecurity.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;
import reactor.core.publisher.Hooks;

@SpringBootApplication
// Warning! This implies @ComponentScan - and we really must have that in place, e.g. due to JMS :(

@EnableScheduling
@Import({ BasicAuthenticationSpringConfiguration.class, SecurityConfig.class, ErrorSpringConfiguration.class, JMSSpringConfiguration.class, AuthenticatorSpringConfiguration.class })
@EnableAsync
public class PromregatorApplication {
	private static final Logger log = LoggerFactory.getLogger(PromregatorApplication.class);
	
	@Value("${promregator.simulation.enabled:false}")
	private boolean simulationMode;

	@Value("${promregator.simulation.instances:10}")
	private int simulationInstances;
	
	@Value("${promregator.reactor.debug:false}")
	private boolean reactorDebugEnabled;
	
	@Value("${promregator.workaround.dnscache.timeout:-1}")
	private int javaDnsCacheWorkaroundTimeout;

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
	public CFAccessor mainCFAccessor() {
		CFAccessor mainAccessor = null;
		
		if (this.simulationMode) {
			mainAccessor = new CFAccessorSimulator(this.simulationInstances);
		} else {
			mainAccessor = new ReactiveCFAccessorImpl();
		}
		
		return mainAccessor;
	}
	
	@Bean
	public CFAccessorCache cfAccessorCache(CFAccessor mainCFAccessor) {
		return new CFAccessorCacheCaffeine(mainCFAccessor);
	}
	
	@Bean
	public CFAccessor cfAccessor(CFAccessorCache cfAccessorCache) {
		return cfAccessorCache;
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
	public CFMultiDiscoverer cfDiscoverer() {
		return new CFMultiDiscoverer();
	}
	
	@Bean
	public InstanceLifecycleHandler instanceLifecycleHandler() {
		return new InstanceLifecycleHandler();
	}
	
	@Bean
	public InstanceCache instanceCache() {
		return new InstanceCache();
	}
	
	@Bean
	public CollectorRegistry collectorRegistry() {
		CollectorRegistry cr = CollectorRegistry.defaultRegistry;
		
		DefaultExports.initialize();
		
		return cr;
	}
	
	@Bean
	@DependsOn("promregatorConfiguration")
	public ConfigurationValidations configurationValidations() {
		return new ConfigurationValidations();
	}
	
	@Bean
	public InternalMetrics internalMetrics() {
		return new InternalMetrics();
	}

	/**
	 * The number of threads of the scraping thread pool.
	 * The value is coming from the deprecated configuration option <pre>promregator.endpoint.threads</pre>.
	 * Use threadPoolSize instead
	 * @deprecated
	 */
	@Value("${promregator.endpoint.threads:#{null}}")
	@Deprecated
	private Optional<Integer> threadPoolSizeOld;

	@Value("${promregator.scraping.threads:5}")
	private int threadPoolSize;
	
	@PostConstruct
	public void warnOnDeprecatedThreadValue() {
		if (this.threadPoolSizeOld.isPresent()) {
			log.warn("You are still using the deprecated option promregator.endpoint.threads. "
					+ "Please switch to promregator.scraping.threads (same meaning) instead and remove the old one.");
		}
	}
	
	@Bean
	public ExecutorService metricsFetcherPool() {
		log.info(String.format("Thread Pool size is set to %d", this.getThreadPoolSize()));
		return Executors.newFixedThreadPool(this.threadPoolSize);
	}
	
	private int getThreadPoolSize() {
		if (this.threadPoolSize != 5) {
			// different value than the default, so someone must have set it explicitly.
			return this.threadPoolSize;
		}
		
		if (this.threadPoolSizeOld.isPresent()) {
			// the deprecated value still is set; use that one
			return this.threadPoolSizeOld.get();
		}
		
		return this.threadPoolSize; // which means: 5
	}

	/* see also https://github.com/promregator/promregator/issues/54 */
	@Scheduled(fixedRateString = "${promregator.gc.rate:1200}000")
	@SuppressFBWarnings(value="DM_GC", justification="Similar situation as with RMI, which uses this approach")
	public void forceGC() {
		log.info("Triggering major garbage collection");
		System.gc();
	}
	
	/**
	 * a unique identifier for the currently running instance of Promregator
	 * esp. required for detecting loopbacking scraping requests
	 * @return the unique identifier of the currently running instance
	 */
	@Bean
	public UUID promregatorInstanceIdentifier() {
		return UUID.randomUUID();
	}
	
	@PostConstruct
	public void javaDnsCacheWorkaroundTimeout() {
		if (this.javaDnsCacheWorkaroundTimeout != -1) {
			// see also https://docs.aws.amazon.com/de_de/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
			// and https://github.com/promregator/promregator/issues/84
			log.info(String.format("Enabling JVM DNS Cache Workaround with TTL value %d", this.javaDnsCacheWorkaroundTimeout));
			java.security.Security.setProperty("networkaddress.cache.ttl", this.javaDnsCacheWorkaroundTimeout+"");
		}
	}
}
