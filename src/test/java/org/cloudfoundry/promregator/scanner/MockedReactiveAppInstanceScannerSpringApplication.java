package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedReactiveAppInstanceScannerSpringApplication {
	
	@Bean
	public InternalMetrics internalMetrics() {
		return new InternalMetrics();
	}

	@Bean
	public CFAccessor cfAccessor() {
		return new CFAccessorMock();
	}
	
	@Bean
	public AppInstanceScanner appInstanceScanner(CFAccessor cfAccessor) {
		return new ReactiveAppInstanceScanner(cfAccessor);
	}
	
}
