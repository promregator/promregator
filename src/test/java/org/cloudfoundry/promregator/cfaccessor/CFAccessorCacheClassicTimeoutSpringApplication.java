package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
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
public class CFAccessorCacheClassicTimeoutSpringApplication {

	@Bean
	public InternalMetrics internalMetrics() {
		return Mockito.mock(InternalMetrics.class);
	}
	
	private static class TimeoutMonoAnswer implements Answer<Mono<?>> {
		@Override
		public Mono<?> answer(InvocationOnMock invocation) throws Throwable {
			return Mono.delay(Duration.ofMillis(100)).flatMap(e -> Mono.error(new TimeoutException("Unit test timeout raised")));
		}
	}
	
	@Bean
	public CFAccessor parentMock() {
		CFAccessor mock = Mockito.mock(CFAccessor.class);
		Mockito.when(mock.retrieveOrgId("dummy")).then(new TimeoutMonoAnswer());
		Mockito.when(mock.retrieveSpaceId("dummy1", "dummy2")).then(new TimeoutMonoAnswer());
		Mockito.when(mock.retrieveAllApplicationIdsInSpace("dummy1", "dummy2")).then(new TimeoutMonoAnswer());
		Mockito.when(mock.retrieveSpaceSummary("dummy")).then(new TimeoutMonoAnswer());
		Mockito.when(mock.retrieveAllDomains("dummy")).then(new TimeoutMonoAnswer());
		return mock;
	}
	
	@Bean
	public CFAccessorCacheClassic subject(@Qualifier("parentMock") CFAccessor parentMock) {
		return new CFAccessorCacheClassic(parentMock);
	}
	
}
