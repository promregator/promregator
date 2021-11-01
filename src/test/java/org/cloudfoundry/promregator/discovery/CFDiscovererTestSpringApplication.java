package org.cloudfoundry.promregator.discovery;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@EnableConfigurationProperties(PromregatorConfiguration.class)
@SpringBootApplication
@Import({JMSSpringConfiguration.class})
public class CFDiscovererTestSpringApplication {
	
	@Bean
	public TargetResolver targetResolver() {
		return Mockito.mock(TargetResolver.class);
	}

	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new ReactiveAppInstanceScanner();
	}

	@Bean
	public InternalMetrics internalMetrics() {
		return new InternalMetrics();
	}

	@Bean
	public CFAccessor cfAccessor() {
		return new CFAccessorMock();
	}
	
	@Bean
	public Clock clock() {
		return Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), ZoneId.of("UTC"));
	}

	@Bean
	public CFMultiDiscoverer cfDiscoverer() {
		return new CFMultiDiscoverer();
	}
	
}
