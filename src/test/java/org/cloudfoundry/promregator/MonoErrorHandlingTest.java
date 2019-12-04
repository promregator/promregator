package org.cloudfoundry.promregator;

import java.time.Duration;
import java.time.Instant;

import org.junit.Assert;
import org.junit.Test;

import reactor.core.publisher.Mono;

public class MonoErrorHandlingTest {
	@Test
	public void test() {
		boolean raised = false;
		try {
			Mono.just(1).map(v -> {
				System.err.println("Waiting " + Instant.now());
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
				}
				System.err.println("returning " + Instant.now());
				return v;
			}).timeout(Duration.ofMillis(100)).retry(2).doOnError(x -> {
				System.err.println("Exception Handling " + Instant.now());
				x.printStackTrace(System.err);
			}).block();
		} catch (RuntimeException re) {
			raised = true;
		}
		Assert.assertTrue(raised);
	}
}
