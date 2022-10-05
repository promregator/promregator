package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMockV3;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedReactiveTargetResolverSpringApplicationCFAPIV3 {
	
	@Bean
	public CFAccessor cfAccessor() {
		return Mockito.spy(new CFAccessorMockV3());
	}
	
	@Bean
	public TargetResolver targetResolver() {
		return new ReactiveTargetResolver();
	}
	
}
