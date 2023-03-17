package org.cloudfoundry.promregator.endpoint;

import java.time.Clock;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.messagebus.MessageBus;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import io.prometheus.client.CollectorRegistry;

@Configuration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = { TypeExcludeFilter.class }),
		@Filter(type = FilterType.CUSTOM, classes = { AutoConfigurationExcludeFilter.class }),
		@Filter(type = FilterType.ASSIGNABLE_TYPE, value=SingleTargetMetricsEndpoint.class),
		// NB: Handling is taken over by TestableSingleTargetMetricsEndpoint! That one is
		// NOT excluded
		@Filter(type = FilterType.ASSIGNABLE_TYPE, value=InvalidateCacheEndpoint.class)
})
@Import({ PromregatorConfiguration.class })
public class NoTargetsConfiguredSpringApplication {
	public static final UUID currentPromregatorInstanceIdentifier = UUID.randomUUID();
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new AppInstanceScanner() {

			@Override
			public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets, @Nullable Predicate<? super String> applicationIdFilter, @Nullable Predicate<? super Instance> instanceFilter) {
				LinkedList<Instance> result = new LinkedList<>();

				return result;
			}

		};
	}

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
	
	@Bean
	public TargetResolver targetResolver() {
		return Mockito.mock(TargetResolver.class);
	}
	
	@Bean
	public CFMultiDiscoverer cfDiscoverer() {
		return new CFMultiDiscoverer();
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
	public AuthenticatorController authenticatorController() {
		return new AuthenticatorController();
	}
	
	@Bean
	public AuthenticationEnricher authenticationEnricher() {
		return new NullEnricher();
	}

	@Bean
	public UUID promregatorInstanceIdentifier() {
		return currentPromregatorInstanceIdentifier;
	}

	public static HttpServletRequest mockedHttpServletRequest = Mockito.mock(HttpServletRequest.class);
	
	@Bean
	public HttpServletRequest httpServletRequest() {
		return mockedHttpServletRequest;
	}
	
	@Bean
	public MessageBus messageBus() {
		return new MessageBus();
	}
}
