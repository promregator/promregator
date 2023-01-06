package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class RequestAggregatorTest {

	private static final String EXCEPTION_DETECTED = "exception detected";

	private static class ReqeuestAggregatorUnderTest extends RequestAggregator<Integer, String> {

		public ReqeuestAggregatorUnderTest() {
			super(Integer.class, String.class);
		}

		@Override
		protected Mono<String> sendRequest(List<Integer> block) {
			List<String> stringList = block.stream().map(i -> i+"").toList();
			
			return Mono.just(String.join(",", stringList));
		}

		@Override
		protected Map<Integer, String> determineMapOfResponses(String response) {
			String[] strings = response.split(",");
			
			Map<Integer, String> result = new HashMap<>();
			List.of(strings).forEach(s -> {
				result.put(Integer.parseInt(s), s);
			});
			return result;
		}
		
	}
	
	@Test
	void testStraightForwardCase() throws InterruptedException, ExecutionException {
		// for debugging:
		Awaitility.setDefaultTimeout(Duration.ofMinutes(5));
		
		ReqeuestAggregatorUnderTest subject = new ReqeuestAggregatorUnderTest();
		
		try {
			final CompletableFuture<String> future1 = new CompletableFuture<>();
			subject.addToQueue(1, future1);
	
			final CompletableFuture<String> future2 = new CompletableFuture<>();
			subject.addToQueue(2, future2);
			
			Assertions.assertFalse(future1.isDone());
			Assertions.assertFalse(future2.isDone());
			
			Awaitility.await().until(future1::isDone);
			Awaitility.await().until(future2::isDone);
			
			Assertions.assertEquals("1", future1.get());
			Assertions.assertEquals("2", future2.get());
		} finally {
			subject.stop();
		}
	}
	
	@Test
	void testTwoBlocks() throws InterruptedException, ExecutionException {
		// for debugging:
		Awaitility.setDefaultTimeout(Duration.ofMinutes(5));
		
		ReqeuestAggregatorUnderTest subject = new ReqeuestAggregatorUnderTest();
		
		try {
			final CompletableFuture<String> future1 = new CompletableFuture<>();
			final CompletableFuture<String> future2 = new CompletableFuture<>();
			Assertions.assertFalse(future1.isDone());
			Assertions.assertFalse(future2.isDone());

			subject.addToQueue(1, future1);
	
			Awaitility.await().until(future1::isDone);
			Assertions.assertEquals("1", future1.get());
			
			Assertions.assertFalse(future2.isDone());
			
			subject.addToQueue(2, future2);
			
			Awaitility.await().until(future2::isDone);
			Assertions.assertEquals("2", future2.get());
		} finally {
			subject.stop();
		}
	}
	
	private static class ReqeuestAggregatorDropsSecondItem extends RequestAggregator<Integer, String> {

		public ReqeuestAggregatorDropsSecondItem() {
			super(Integer.class, String.class);
		}

		@Override
		protected Mono<String> sendRequest(List<Integer> block) {
			List<String> stringList = block.stream().map(i -> i+"").toList();
			
			return Mono.just(String.join(",", stringList));
		}

		@Override
		protected Map<Integer, String> determineMapOfResponses(String response) {
			String[] strings = response.split(",");
			
			Map<Integer, String> result = new HashMap<>();
			List.of(strings).forEach(s -> {
				result.put(Integer.parseInt(s), s);
			});
			
			result.remove(2);
			
			return result;
		}
		
	}
	
	@Test
	void testSecondItemDropped() throws InterruptedException, ExecutionException {
		// for debugging:
		Awaitility.setDefaultTimeout(Duration.ofMinutes(5));
		
		ReqeuestAggregatorDropsSecondItem subject = new ReqeuestAggregatorDropsSecondItem();
		
		try {
			final CompletableFuture<String> future1 = new CompletableFuture<>();
			subject.addToQueue(1, future1);
	
			final CompletableFuture<String> future2 = new CompletableFuture<>();
			subject.addToQueue(2, future2);
			
			Awaitility.await().until(future1::isDone);
			Awaitility.await().until(future2::isDone);
			
			Assertions.assertEquals("1", future1.get());
			Assertions.assertNull(future2.get());
		} finally {
			subject.stop();
		}
	}
	
	private static class ReqeuestAggregatorRaisingException extends RequestAggregator<Integer, String> {

		public ReqeuestAggregatorRaisingException() {
			super(Integer.class, String.class);
		}

		@Override
		protected Mono<String> sendRequest(List<Integer> block) {
			return Mono.error(new Exception("Did not work"));
		}

		@Override
		protected Map<Integer, String> determineMapOfResponses(String response) {
			Assertions.fail("Should never be called");
			return null;
		}
		
	}
	
	@Test
	void testExcedptionRaised() throws InterruptedException, ExecutionException {
		// for debugging: Awaitility.setDefaultTimeout(Duration.ofMinutes(5));
		Awaitility.setDefaultPollInterval(Duration.ofMillis(10));
		
		ReqeuestAggregatorRaisingException subject = new ReqeuestAggregatorRaisingException();

		try {
			final CompletableFuture<String> future1 = new CompletableFuture<>();
			subject.addToQueue(1, future1);
	
			final CompletableFuture<String> future2 = new CompletableFuture<>();
			subject.addToQueue(2, future2);
			
			Awaitility.await().until(future1::isDone);
			Awaitility.await().until(future2::isDone);
			
			Assertions.assertTrue(future1.isCompletedExceptionally());
			Assertions.assertTrue(future2.isCompletedExceptionally());
			
			String result = future1.whenComplete( (e, t) -> {
				Assertions.assertTrue(t instanceof Exception);
			}).exceptionally(t -> EXCEPTION_DETECTED).get();
			
			Assertions.assertEquals(EXCEPTION_DETECTED, result);
		} finally {
			subject.stop();
		}
	}

}
