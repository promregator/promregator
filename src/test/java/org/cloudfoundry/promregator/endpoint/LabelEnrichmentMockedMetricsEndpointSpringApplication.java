package org.cloudfoundry.promregator.endpoint;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import jakarta.annotation.Nullable;
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
		@Filter(type = FilterType.ASSIGNABLE_TYPE, value = TestableSingleTargetMetricsEndpoint.class ),
		/* NB: We want to have a the real SingleTargetMetricsEndpoint and not the TestableSingleTargetMetricsEndpoint here! 
		 * That's why TestableSingleTargetMetricsEndpoint is excluded, but SingleTargetMetricsEndpoint is not (which is the opposite of a
		 * very similar test)!
		 */
		@Filter(type = FilterType.ASSIGNABLE_TYPE, value=InvalidateCacheEndpoint.class)
})
@Import({ PromregatorConfiguration.class })
public class LabelEnrichmentMockedMetricsEndpointSpringApplication {
	public static final UUID currentPromregatorInstanceIdentifier = UUID.randomUUID();
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new AppInstanceScanner() {

			@Override
			public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets, @Nullable Predicate<? super String> applicationIdFilter, @Nullable Predicate<? super Instance> instanceFilter) {
				LinkedList<Instance> result = new LinkedList<>();

				ResolvedTarget t = new ResolvedTarget();
				t.setOrgName("unittestorg");
				t.setSpaceName("unittestspace");
				t.setApplicationName("unittestapp");
				t.setPath("/metrics");
				t.setProtocol("http");
				result.add(new Instance(t, "faedbb0a-2273-4cb4-a659-bd31331f7daf:0", "http://localhost:9002/metrics", false)); // Must be the same port as in MetricsEndpointMockServer

				if (applicationIdFilter != null) {
					for (Iterator<Instance> it = result.iterator(); it.hasNext();) {
						Instance instance = it.next();
						if (!applicationIdFilter.test(instance.getApplicationId()))
							it.remove();
					}
				}
				
				if (instanceFilter != null) {
					for (Iterator<Instance> it = result.iterator(); it.hasNext();) {
						Instance instance = it.next();
						if (!instanceFilter.test(instance))
							it.remove();
					}
				}
				
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
