package org.cloudfoundry.promregator.cfaccessor;

import java.util.concurrent.TimeoutException;

import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

@Configuration
public class CFAccessorCacheCaffeineTimeoutSpringApplication {

	@Bean
	public InternalMetrics internalMetrics() {
		return Mockito.mock(InternalMetrics.class);
	}
	
	@Bean
	public CFAccessor parentMock() {
		return Mockito.mock(CFAccessor.class);
	}
	
	@Bean
	public CFAccessorCacheCaffeine subject(@Qualifier("parentMock") CFAccessor parentMock) {
		return new CFAccessorCacheCaffeine(parentMock);
	}
	
}
