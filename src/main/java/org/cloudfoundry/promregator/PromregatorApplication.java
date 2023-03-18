package org.cloudfoundry.promregator;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.cloudfoundry.promregator.cfaccessor.AccessorCacheType;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorCache;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorCacheCaffeine;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorSimulator;
import org.cloudfoundry.promregator.cfaccessor.CFWatchdog;
import org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl;
import org.cloudfoundry.promregator.config.ConfigurationValidations;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.lifecycle.InstanceLifecycleHandler;
import org.cloudfoundry.promregator.messagebus.MessageBus;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.CachingTargetResolver;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveTargetResolver;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.cloudfoundry.promregator.springconfig.AuthenticatorSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.BasicAuthenticationSpringConfiguration;
import org.cloudfoundry.promregator.springconfig.ErrorSpringConfiguration;
import org.cloudfoundry.promregator.websecurity.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
@Import({ BasicAuthenticationSpringConfiguration.class, SecurityConfig.class, ErrorSpringConfiguration.class, AuthenticatorSpringConfiguration.class })
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

	@Value("${cf.cache.type:CAFFEINE}")
	// NB: Spring supports configuration values for enums to be both upper- and lowercased
	private AccessorCacheType cacheType;
	
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
	public CFWatchdog cfWatchdog() {
		return new CFWatchdog();
	}
	
	@Bean
	public CFAccessorCache cfAccessorCache(@Qualifier("mainCFAccessor") CFAccessor cfMainAccessor) {
		if (this.cacheType == AccessorCacheType.CAFFEINE) {
			return new CFAccessorCacheCaffeine(cfMainAccessor);
		} else {
			throw new UnknownCacheTypeError("Unknown CF Accessor Cache selected: "+this.cacheType);
		}
	}
	
	private static class UnknownCacheTypeError extends Error {
		private static final long serialVersionUID = 6158818763963263064L;

		public UnknownCacheTypeError(String message) {
			super(message);
		}
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
	
	@PostConstruct
	public void registerDefaultExportsAsCollectorRegistry() {
		DefaultExports.initialize();
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
	 */
	@Value("${promregator.scraping.threads:5}")
	private int threadPoolSize;
	
	
	@Bean
	public ExecutorService metricsFetcherPool() {
		log.info("Thread Pool size is set to {}", this.getThreadPoolSize());
		return Executors.newFixedThreadPool(this.threadPoolSize);
	}
	
	private int getThreadPoolSize() {
		return this.threadPoolSize;
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
	
	@Bean
	public MessageBus messageBus() {
		return new MessageBus();
	}
	
	@PostConstruct
	public void javaDnsCacheWorkaroundTimeout() {
		if (this.javaDnsCacheWorkaroundTimeout != -1) {
			// see also https://docs.aws.amazon.com/de_de/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
			// and https://github.com/promregator/promregator/issues/84
			log.info("Enabling JVM DNS Cache Workaround with TTL value {}", this.javaDnsCacheWorkaroundTimeout);
			java.security.Security.setProperty("networkaddress.cache.ttl", this.javaDnsCacheWorkaroundTimeout+"");
		}
	}
}
