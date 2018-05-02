package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.cloudfoundry.promregator.scanner.TrivialTargetResolver;
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
		@Filter(type = FilterType.REGEX, pattern = "org\\.cloudfoundry\\.promregator\\.endpoint\\.MetricsEndpoint")
		// NB: Handling is taken over by TestableMetricsEndpoint! That one is
		// NOT excluded
})
@Import({ PromregatorConfiguration.class })
public class MockedMetricsEndpointSpringApplication {
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new AppInstanceScanner() {

			@Override
			public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets) {
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

				return result;
			}

		};
	}

	@Bean
	public TargetResolver targetResolver() {
		return new TrivialTargetResolver();
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
	public AuthenticationEnricher authenticationEnricher() {
		return new NullEnricher();
	};

}
