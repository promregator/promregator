package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedReactiveTargetResolverSpringApplication {
	
	@Bean
	public CFAccessor cfAccessor() {
		return Mockito.spy(new CFAccessorMock());
	}
	
	@Bean
	public TargetResolver targetResolver(CFAccessor cfAccessor) {
		return new ReactiveTargetResolver(cfAccessor);
	}
	
}
