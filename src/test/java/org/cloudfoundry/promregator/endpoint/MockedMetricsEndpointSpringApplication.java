package org.cloudfoundry.promregator.endpoint;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Null;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import io.prometheus.client.CollectorRegistry;
import reactor.core.publisher.Mono;
import org.springframework.jms.core.JmsTemplate;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties({CloudFoundryConfiguration.class, PromregatorConfiguration.class })
public class MockedMetricsEndpointSpringApplication {
	public static final UUID currentPromregatorInstanceIdentifier = UUID.randomUUID();

	@Bean
	public TestableSingleTargetMetricsEndpoint testableSingleTargetMetricsEndpoint(PromregatorConfiguration promregatorConfiguration,
																				   ExecutorService metricsFetcherPool,
																				   AuthenticatorController authenticatorController,
																				   UUID promregatorInstanceIdentifier,
																				   InstanceCache instanceCache) {
		return new TestableSingleTargetMetricsEndpoint(promregatorConfiguration, metricsFetcherPool, authenticatorController, promregatorInstanceIdentifier, instanceCache);
	}

	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new AppInstanceScanner() {

			@Override
			public Mono<List<Instance>> determineInstancesFromTargets(List<ResolvedTarget> targets, @Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) {
				LinkedList<Instance> result = new LinkedList<>();

				ResolvedTarget t = new ResolvedTarget();
				t.setOrgName("unittestorg");
				t.setSpaceName("unittestspace");
				t.setApplicationName("unittestapp");
				t.setPath("/path");
				t.setProtocol("https");
				result.add(new Instance(t, "faedbb0a-2273-4cb4-a659-bd31331f7daf:0", "http://localhost:1234"));
				result.add(new Instance(t, "faedbb0a-2273-4cb4-a659-bd31331f7daf:1", "http://localhost:1234"));

				t = new ResolvedTarget();
				t.setOrgName("unittestorg");
				t.setSpaceName("unittestspace");
				t.setApplicationName("unittestapp2");
				t.setPath("/otherpath");
				t.setProtocol("http");
				result.add(new Instance(t, "1142a717-e27d-4028-89d8-b42a0c973300:0", "http://localhost:1235"));

				if (applicationIdFilter != null) {
					result.removeIf(instance -> !applicationIdFilter.test(instance.getApplicationId()));
				}
				
				if (instanceFilter != null) {
					result.removeIf(instance -> !instanceFilter.test(instance));
				}
				
				return Mono.just(result);
			}

		};
	}

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
	
	@Bean
	public TargetResolver targetResolver() {
		TargetResolver resolver = Mockito.mock(TargetResolver.class);
		when(resolver.resolveTargets(anyList())).thenReturn(Mono.empty());
		return resolver;
	}
	
	@Bean
	public CFMultiDiscoverer cfDiscoverer(TargetResolver targetResolver, AppInstanceScanner appInstanceScanner, PromregatorConfiguration promregatorConfiguration, JmsTemplate jmsTemplate, Clock clock) {
		return new CFMultiDiscoverer(targetResolver, appInstanceScanner, promregatorConfiguration, jmsTemplate, clock);
	}
	
	@Bean
	public ExecutorService metricsFetcherPool() {
		return Executors.newSingleThreadExecutor();
	}

	@Bean
	public CollectorRegistry collectorRegistry() {
		return CollectorRegistry.defaultRegistry;
	}

	@Bean
	public AuthenticatorController authenticatorController(PromregatorConfiguration promregatorConfiguration) {
		return new AuthenticatorController(promregatorConfiguration);
	}
	
	@Bean
	public AuthenticationEnricher authenticationEnricher() {
		return new NullEnricher();
	}

	@Bean
	public UUID promregatorInstanceIdentifier() {
		return currentPromregatorInstanceIdentifier;
	}

	@Bean
	public InstanceCache instanceCache() {
		return new InstanceCache();
	}
	
	public static HttpServletRequest mockedHttpServletRequest = Mockito.mock(HttpServletRequest.class);
	
	@Bean
	public HttpServletRequest httpServletRequest() {
		return mockedHttpServletRequest;
	}
}
