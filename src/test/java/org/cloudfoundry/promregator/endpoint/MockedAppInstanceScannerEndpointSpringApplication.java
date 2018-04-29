package org.cloudfoundry.promregator.endpoint;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
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
		@Filter(type = FilterType.REGEX, pattern = "org\\.cloudfoundry\\.promregator\\.endpoint\\.MetricsEndpoint")
		// NB: Handling is taken over by TestableMetricsEndpoint! That one is
		// NOT excluded
})
@Import({ PromregatorConfiguration.class })
public class MockedAppInstanceScannerEndpointSpringApplication {
	public static class MockedReactiveAppInstanceScanner extends ReactiveAppInstanceScanner {
		private boolean appInvalidated;
		private boolean spaceInvalidated;
		private boolean orgInvalidated;
		
		@Override
		public void invalidateCacheApplications() {
			this.appInvalidated = true;
		}

		@Override
		public void invalidateCacheSpace() {
			this.spaceInvalidated = true;
		}

		@Override
		public void invalidateCacheOrg() {
			this.orgInvalidated = true;
		}

		public boolean isAppInvalidated() {
			return appInvalidated;
		}

		public boolean isSpaceInvalidated() {
			return spaceInvalidated;
		}

		public boolean isOrgInvalidated() {
			return orgInvalidated;
		}
	}
	
	@Bean
	public CFAccessor cfAccessor() {
		return Mockito.mock(CFAccessor.class);
	}
	
	@Bean
	public InternalMetrics internalMetrics() {
		return Mockito.mock(InternalMetrics.class);
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new MockedReactiveAppInstanceScanner();
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
