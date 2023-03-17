package org.cloudfoundry.promregator.endpoint;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.AuthenticatorController;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorCacheCaffeine;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.messagebus.MessageBus;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.CachingTargetResolver;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

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
@TestPropertySource(locations="../default.properties")
public class MockedAppInstanceScannerEndpointSpringApplication {
	
	public static class MockedCFAccessorCache extends CFAccessorCacheCaffeine {
		public MockedCFAccessorCache() {
			super(null);
		}

		private boolean applicationCache = false;
		private boolean spaceCache = false;
		private boolean orgCache = false;
		private boolean routeCache = false;
		private boolean domainCache = false;
		private boolean processCache = false;
		
		@Override
		public void invalidateCacheApplication() {
			this.applicationCache = true;
		}

		@Override
		public void invalidateCacheSpace() {
			this.spaceCache = true;
		}

		@Override
		public void invalidateCacheOrg() {
			this.orgCache = true;
		}
		
		@Override
		public void invalidateCacheDomain() {
			this.domainCache = true;
		}

		@Override
		public void invalidateCacheRoute() {
			this.routeCache = true;
		}

		@Override
		public void invalidateCacheProcess() {
			this.processCache = true;
		}

		public boolean isApplicationCache() {
			return applicationCache;
		}

		public boolean isSpaceCache() {
			return spaceCache;
		}

		public boolean isOrgCache() {
			return orgCache;
		}
		
		public boolean isDomainCache() {
			return domainCache;
		}
		
		public boolean isRouteCache() {
			return routeCache;
		}
		
		public boolean isProcessCache() {
			return processCache;
		}
		
	}
	
	public static class MockedCachingTargetResolver extends CachingTargetResolver {
		public MockedCachingTargetResolver() {
			super(null);
		}

		private boolean resolverCache = false;
		
		@Override
		public void invalidateCache() {
			this.resolverCache = true;
		}

		public boolean isResolverCache() {
			return this.resolverCache;
		}
	}
	
	@Bean
	public CFAccessor cfAccessor() {
		return new MockedCFAccessorCache();
	}
	
	@Bean
	public CFAccessorCacheCaffeine cfAccessorCache(@Qualifier("cfAccessor") CFAccessor cfAccessor) {
		return (CFAccessorCacheCaffeine) cfAccessor;
	}
	
	@Bean
	public InternalMetrics internalMetrics() {
		return Mockito.mock(InternalMetrics.class);
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new ReactiveAppInstanceScanner();
	}

	@Bean
	public CachingTargetResolver cachingTargetResolver() {
		return new MockedCachingTargetResolver();
	}
	
	@Bean
	public TargetResolver targetResolver(CachingTargetResolver cachingTargetResolver) {
		return cachingTargetResolver;
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
	public MessageBus messageBus() {
		return new MessageBus();
	}
	
}
