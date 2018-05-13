package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = { TypeExcludeFilter.class }),
		@Filter(type = FilterType.CUSTOM, classes = { AutoConfigurationExcludeFilter.class }),
		@Filter(type = FilterType.REGEX, pattern = "org\\.cloudfoundry\\.promregator\\.endpoint\\.MetricsEndpoint")
		// NB: Handling is taken over by TestableMetricsEndpoint! That one is
		// NOT excluded
})
@Import({ JMSSpringConfiguration.class })
public class ResolvedTargetManagerSpringApplication {
	
	@Bean
	public Clock clock() {
		return Clock.fixed(Instant.now(), ZoneId.of("UTC"));
	}
	
	@Bean
	public ResolvedTargetManager subject(Clock clock) {
		return new ResolvedTargetManager(clock);
	}
	
	@Bean
	public TestableResolvedTargetManagerReceiver receiver() {
		return new TestableResolvedTargetManagerReceiver();
	}
	
}
