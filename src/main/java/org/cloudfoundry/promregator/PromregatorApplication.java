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
import org.cloudfoundry.promregator.cfaccessor.CFWatchdog;
import org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl;
import org.cloudfoundry.promregator.config.ConfigurationValidations;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint;
import org.cloudfoundry.promregator.endpoint.InstanceCache;
import org.cloudfoundry.promregator.endpoint.SingleTargetMetricsEndpoint;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jms.core.JmsTemplate;
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
@Import({ BasicAuthenticationSpringConfiguration.class,
		SecurityConfig.class,
		ErrorSpringConfiguration.class,
		JMSSpringConfiguration.class,
		AuthenticatorSpringConfiguration.class,
		DiscoveryEndpoint.class,
		SingleTargetMetricsEndpoint.class})
@EnableConfigurationProperties({PromregatorConfiguration.class})
@EnableAsync
public class PromregatorApplication {
	private static final Logger log = LoggerFactory.getLogger(PromregatorApplication.class);


	public PromregatorApplication(PromregatorConfiguration promregatorConfiguration) {
		enableReactorDebugging(promregatorConfiguration);
		javaDnsCacheWorkaroundTimeout(promregatorConfiguration);
	}

	public static void main(String[] args) {
		SpringApplication.run(PromregatorApplication.class, args);
	}

	public void enableReactorDebugging(PromregatorConfiguration promregatorConfiguration) {
		if (promregatorConfiguration.getReactor().getDebug()) {
			Hooks.onOperatorDebug();
		}
	}
	
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
	
	@Bean
	public CFAccessor mainCFAccessor(Environment env, PromregatorConfiguration promregatorConfiguration) {
		if (promregatorConfiguration.getSimulation().getEnabled()) {
			return new CFAccessorSimulator(promregatorConfiguration.getSimulation().getInstances());
		} else {
			return new ReactiveCFAccessorImpl();
		}
	}
	
	@Bean
	public CFWatchdog cfWatchdog() {
		return new CFWatchdog();
	}
	
	@Bean
	public CFAccessorCache cfAccessorCache(@Value("${cf.cache.timeout.org:3600}") int refreshCacheOrgLevelInSeconds,
										   @Value("${cf.cache.timeout.space:3600}") int refreshCacheSpaceLevelInSeconds,
										   @Value("${cf.cache.timeout.application:300}") int refreshCacheApplicationLevelInSeconds,
										   @Value("${cf.cache.expiry.org:120}") int expiryCacheOrgLevelInSeconds,
										   @Value("${cf.cache.expiry.space:120}") int expiryCacheSpaceLevelInSeconds,
										   @Value("${cf.cache.expiry.application:120}") int expiryCacheApplicationLevelInSeconds,
										   InternalMetrics internalMetrics,
										   CFAccessor mainCFAccessor) {
		return new CFAccessorCacheCaffeine(refreshCacheOrgLevelInSeconds,
				refreshCacheSpaceLevelInSeconds,
				refreshCacheApplicationLevelInSeconds,
				expiryCacheOrgLevelInSeconds,
				expiryCacheSpaceLevelInSeconds,
				expiryCacheApplicationLevelInSeconds, internalMetrics, mainCFAccessor);
	}
	
	@Bean
	public CFAccessor cfAccessor(CFAccessorCache cfAccessorCache) {
		return cfAccessorCache;
	}
	
	@Bean
	public ReactiveTargetResolver reactiveTargetResolver(CFAccessor cfAccessor) {
		return new ReactiveTargetResolver(cfAccessor);
	}
	
	@Bean
	public CachingTargetResolver cachingTargetResolver(ReactiveTargetResolver reactiveTargetResolver, @Value("${cf.cache.timeout.resolver:300}") int timeout) {
		return new CachingTargetResolver(reactiveTargetResolver, timeout);
	}
	
	@Bean
	public TargetResolver targetResolver(CachingTargetResolver cachingTargetResolver) {
		return cachingTargetResolver;
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner(CFAccessor cfAccessor) {
		return new ReactiveAppInstanceScanner(cfAccessor);
	}
	
	@Bean
	public CFMultiDiscoverer cfDiscoverer(TargetResolver targetResolver, AppInstanceScanner appInstanceScanner, PromregatorConfiguration promregatorConfiguration, JmsTemplate jmsTemplate, Clock clock) {
		return new CFMultiDiscoverer(targetResolver, appInstanceScanner, promregatorConfiguration, jmsTemplate, clock);
	}
	
	@Bean
	@ConditionalOnProperty(value = "promregator.lifecycle.enabled", matchIfMissing = true)
	public InstanceLifecycleHandler instanceLifecycleHandler() {
		return new InstanceLifecycleHandler();
	}
	
	@Bean
	public InstanceCache instanceCache(CFDiscoverer discoverer) {
		return new InstanceCache(discoverer);
	}
	
	@Bean
	public CollectorRegistry collectorRegistry() {
		CollectorRegistry cr = CollectorRegistry.defaultRegistry;
		
		DefaultExports.initialize();
		
		return cr;
	}
	
	@Bean
	public ConfigurationValidations configurationValidations(PromregatorConfiguration promregatorConfiguration) {
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

	public void javaDnsCacheWorkaroundTimeout(PromregatorConfiguration promregatorConfiguration) {
		int javaDnsCacheWorkaroundTimeout = promregatorConfiguration.getWorkaround().getDnscache().getTimeout();
		if (javaDnsCacheWorkaroundTimeout != -1) {
			// see also https://docs.aws.amazon.com/de_de/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
			// and https://github.com/promregator/promregator/issues/84
			log.info(String.format("Enabling JVM DNS Cache Workaround with TTL value %d", javaDnsCacheWorkaroundTimeout));
			java.security.Security.setProperty("networkaddress.cache.ttl", javaDnsCacheWorkaroundTimeout+"");
		}
	}
}
