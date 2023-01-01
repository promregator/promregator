package org.cloudfoundry.promregator.cache;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.testing.FakeTicker;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class CaffeineAsyncLoadingTest {
	private static final Logger log = LoggerFactory.getLogger(CaffeineAsyncLoadingTest.class);

	private static final class AsyncCacheLoaderTimingImplementation implements AsyncCacheLoader<String, Integer> {
		private int executionNumber = 0;
		
		@Override
		public @NonNull CompletableFuture<Integer> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {

			log.info(String.format("Request loading iteration %d for request %s", this.executionNumber, key));
			Mono<Integer> result = null;
			
			synchronized(this) {
				result = Mono.just(executionNumber++);
			}
			
			result = result.subscribeOn(Schedulers.fromExecutor(executor));
			
			if (this.executionNumber > 1) {
				result = result.map( x-> {
					log.info(String.format("Starting to delay - iteration: %d", x));
					return x;
				}).delayElement(Duration.ofMillis(200))
				.map( x-> {
					log.info(String.format("Finished delaying - iteration: %d", x));
					return x;
				});
			}
			
			result = result.cache();
			
			return result.toFuture();
		}
	}

	@Test
	public void testRefreshIsAsynchronous() throws InterruptedException {
		FakeTicker ticker = new FakeTicker();
		
		AsyncLoadingCache<String, Integer> subject = Caffeine.newBuilder()
				.expireAfterAccess(240, TimeUnit.SECONDS)
				.refreshAfterWrite(120, TimeUnit.SECONDS)
				.ticker(ticker::read)
				.recordStats()
				.buildAsync(new AsyncCacheLoaderTimingImplementation());
		
		log.info("Starting first request");
		Assertions.assertEquals(Integer.valueOf(0), Mono.fromFuture(subject.get("a")).block());
		log.info("Stats on cache: "+subject.synchronous().stats().toString());
		
		ticker.advance(Duration.ofSeconds(10));
		
		log.info("Sending second request");
		Assertions.assertEquals(Integer.valueOf(0), Mono.fromFuture(subject.get("a")).block());
		log.info("Stats on cache: "+subject.synchronous().stats().toString());
		
		ticker.advance(Duration.ofSeconds(120));
		
		log.info("Sending third request");
		Assertions.assertEquals(Integer.valueOf(0), Mono.fromFuture(subject.get("a")).block());
		// That's the interesting case here! Note the zero above: This means that we get old cache data (which is what we want!)
		log.info("Stats on cache: "+subject.synchronous().stats().toString());

		ticker.advance(Duration.ofSeconds(10));
		Thread.sleep(250); // wait until async loading took place
		
		log.info("Sending fourth request");
		Assertions.assertEquals(Integer.valueOf(1), Mono.fromFuture(subject.get("a")).block());
		log.info("Stats on cache: "+subject.synchronous().stats().toString());
		
	}
	
	
	private static final class AsyncCacheLoaderFailureImplementation implements AsyncCacheLoader<String, Integer> {
		private int executionNumber = 0;
		
		@Override
		public @NonNull CompletableFuture<Integer> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {

			log.info(String.format("Request loading iteration %d for request %s", this.executionNumber, key));
			Mono<Integer> result = null;
			
			synchronized(this) {
				result = Mono.just(executionNumber++);
			}
			
			result = result.subscribeOn(Schedulers.fromExecutor(executor)).cache();
			
			if (this.executionNumber > 1) {
				result = Mono.error(new Error("Failure while async loading"));
			}
			
			return result.toFuture();
		}
	}
	@Test
	public void testFailureOnAsynchronous() {
		FakeTicker ticker = new FakeTicker();
		
		AsyncLoadingCache<String, Integer> subject = Caffeine.newBuilder()
				.expireAfterAccess(240, TimeUnit.SECONDS)
				.refreshAfterWrite(120, TimeUnit.SECONDS)
				.ticker(ticker::read)
				.recordStats()
				.buildAsync(new AsyncCacheLoaderFailureImplementation());
		
		Assertions.assertEquals(Integer.valueOf(0), Mono.fromFuture(subject.get("a")).block());
		
		ticker.advance(Duration.ofSeconds(10));
		
		Assertions.assertEquals(Integer.valueOf(0), Mono.fromFuture(subject.get("a")).block());
		
		ticker.advance(Duration.ofSeconds(250));
		
		Mono<Integer> errorMono = Mono.fromFuture(subject.get("a"));
		
		boolean thrown = false;
		try {
			errorMono.block();
			thrown = false;
		} catch (Throwable t) {
			thrown = true;
		}
		Assertions.assertTrue(thrown);
	}
}
