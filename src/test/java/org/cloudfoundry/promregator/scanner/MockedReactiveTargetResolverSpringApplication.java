package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedReactiveTargetResolverSpringApplication {
	
	@Bean
	public CFAccessor cfAccessor() {
		return new CFAccessorMock();
	}
	
	@Bean
	public TargetResolver targetResolver() {
		return new ReactiveTargetResolver();
	}
	
}
