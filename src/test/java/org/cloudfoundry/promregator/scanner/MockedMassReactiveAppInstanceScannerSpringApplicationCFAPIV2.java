package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMassMockV2;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedMassReactiveAppInstanceScannerSpringApplicationCFAPIV2 {
	
	@Bean
	public InternalMetrics internalMetrics() {
		return new InternalMetrics();
	}

	@Bean
	public CFAccessor cfAccessor() {
		return new CFAccessorMassMockV2(10);
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner() {
		return new ReactiveAppInstanceScanner();
	}
	
}
