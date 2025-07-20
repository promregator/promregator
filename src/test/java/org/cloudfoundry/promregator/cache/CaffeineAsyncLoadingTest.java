package org.cloudfoundry.promregator.cache;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.testing.FakeTicker;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CaffeineAsyncLoadingTest {
	private static final Logger log = LoggerFactory.getLogger(CaffeineAsyncLoadingTest.class);

	private static final class AsyncCacheLoaderTimingImplementation implements AsyncCacheLoader<String, Integer> {
		private int executionNumber = 0;
		
		@Override
		public @NonNull CompletableFuture<Integer> asyncLoad(@NonNull String key,
				@NonNull Executor executor) {

			log.info("Request loading iteration {} for request {}", this.executionNumber, key);
			Mono<Integer> result = null;
			
			synchronized(this) {
				result = Mono.just(executionNumber++);
			}
			
			result = result.subscribeOn(Schedulers.fromExecutor(executor));
			
			if (this.executionNumber > 1) {
				result = result.map( x-> {
					log.info("Starting to delay - iteration: {}", x);
					return x;
				}).delayElement(Duration.ofMillis(200))
				.map( x-> {
					log.info("Finished delaying - iteration: {}", x);
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

			log.info("Request loading iteration {} for request {}", this.executionNumber, key);
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

	private static final class CompleterThread extends Thread {

		private CompletableFuture<Integer> future;
		
		CompleterThread(CompletableFuture<Integer> future) {
			this.future = future;
		}
		
		@Override
		public void run() {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			future.complete(1);
		}

	}
	
	private static class AsyncOtherThreadResolver implements AsyncCacheLoader<String, Integer> {

		private int counter = 0;
		
		@Override
		public @NonNull CompletableFuture<Integer> asyncLoad(@NonNull String key, @NonNull Executor executor) {
			counter++;
			
			CompletableFuture<Integer> future = new CompletableFuture<Integer>();
			
			new CompleterThread(future).start();
			
			return future;
		}

		public int getCounter() {
			return counter;
		}
		
	}
	
	@Test
	public void testAsyncCompleteOnOtherThread() throws InterruptedException, ExecutionException, TimeoutException {
		AsyncOtherThreadResolver loader = new AsyncOtherThreadResolver();
		
		AsyncLoadingCache<String, Integer> subject = Caffeine.newBuilder()
				.expireAfterAccess(240, TimeUnit.SECONDS)
				.refreshAfterWrite(120, TimeUnit.SECONDS)
				.buildAsync(loader);
		
		CompletableFuture<Integer> future1 = subject.get("a");
		future1.get(1000L, TimeUnit.SECONDS);
		Assertions.assertEquals(1, loader.getCounter());
		
		Thread.sleep(500);
		
		CompletableFuture<Integer> future2 = subject.get("a");
		future2.get(1000L, TimeUnit.SECONDS);
		
		Assertions.assertEquals(1, loader.getCounter());
	}
}
