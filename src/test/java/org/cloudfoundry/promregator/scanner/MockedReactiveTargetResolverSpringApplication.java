package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMockV2;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedReactiveTargetResolverSpringApplication {
	
	@Bean
	public CFAccessor cfAccessor() {
		return Mockito.spy(new CFAccessorMockV2());
	}
	
	@Bean
	public TargetResolver targetResolver() {
		return new ReactiveTargetResolver();
	}
	
}
