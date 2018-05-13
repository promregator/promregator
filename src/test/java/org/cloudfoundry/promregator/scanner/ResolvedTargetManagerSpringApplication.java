package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResolvedTargetManagerSpringApplication {
	
	@Bean
	public Clock clock() {
		return Clock.fixed(Instant.now(), ZoneId.of("UTC"));
	}
	
	@Bean
	public ResolvedTargetManager subject(Clock clock) {
		return new ResolvedTargetManager(clock);
	}
	
}
