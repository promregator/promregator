package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorSimulator;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedMassReactiveAppInstanceScannerSpringApplication {
	
	@Bean
	public InternalMetrics internalMetrics() {
		return new InternalMetrics();
	}

	@Bean
	public CFAccessor cfAccessor() {
		return new CFAccessorSimulator(10);
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new ReactiveAppInstanceScanner();
	}
	
}
