package org.cloudfoundry.promregator.cache;


import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

class AutoRefreshingCacheMapTest {
	private static final Logger log = LoggerFactory.getLogger(AutoRefreshingCacheMapTest.class);

	@Test
	void testPutAndGet() {
		AutoRefreshingCacheMap<String, String> subject = new AutoRefreshingCacheMap<>("test", null, Duration.ofSeconds(1), Duration.ofSeconds(1), key -> {
			Assertions.fail("should not be reached");
			return null;
		});
		
		subject.put("test", "value");
		String value = subject.get("test");
		
		Assertions.assertEquals("value", value);
	}
	
	@Test
	void testAutoLoad() {
		AutoRefreshingCacheMap<String, String> subject = new AutoRefreshingCacheMap<>("test", null, Duration.ofSeconds(1), Duration.ofSeconds(1), key -> {
			char[] source = key.toCharArray();
			char[] target = new char[source.length];
			for (int i = 0;i<source.length;i++) {
				target[source.length-i-1] = source[i];
			}
			return new String(target);
		});
		
		String value = subject.get("autoload");
		Assertions.assertEquals("daolotua", value);
	}
	
	@Test
	void testExpiry() throws InterruptedException {
		AutoRefreshingCacheMap<String, String> subject = new AutoRefreshingCacheMap<>("test", null, Duration.ofMillis(100), Duration.ofSeconds(10), key -> {
			char[] source = key.toCharArray();
			char[] target = new char[source.length];
			for (int i = 0;i<source.length;i++) {
				target[source.length-i-1] = source[i];
			}
			return new String(target);
		});
		
		String value = subject.get("autoload");
		Assertions.assertEquals("daolotua", value);
		
		// we have to wait until the thread has been called to clean up
		Thread.sleep(1000);
		
		boolean exists = subject.containsKey("autoload");
		Assertions.assertFalse(exists);
	}
	
	private static class Counter {
		private int ctr;
		
		public void increase() {
			this.ctr++;
		}
		public int getCounter() {
			return this.ctr;
		}
	}

	@Test
	void testAutoRefresh() {
		final Counter counter = new Counter();
		final String testKey = "key";
		AutoRefreshingCacheMap<String, String> subject = new AutoRefreshingCacheMap<>("test", null, Duration.ofSeconds(10), Duration.ofMillis(500), key -> {
			counter.increase();
			return "refreshed";
		});

		subject.setRefresherThreadWithIncreasedPriority(true);
		// Note that this also start the refresher thread immediately.
		// It helps keeping this unit test to stay stable.

		subject.put(testKey, "initial");

		Assertions.assertTrue(subject.containsKey(testKey));
		Assertions.assertEquals("initial", subject.get(testKey));
		
		// we have to wait until the thread had a chance to refresh
		await().atMost(3, SECONDS).untilAsserted(() -> Assertions.assertEquals(1, counter.getCounter()));
		Assertions.assertTrue(subject.containsKey(testKey));
		Assertions.assertEquals("refreshed", subject.get(testKey));
	}
	
	private static class MassOperationTestThread extends Thread {
		private AutoRefreshingCacheMap<String, String> subject;
		private int threadNumber;
		
		private boolean terminatedProperly = false;

		public MassOperationTestThread(AutoRefreshingCacheMap<String, String> subject, int threadNumber) {
			this.subject = subject;
			this.threadNumber = threadNumber;
		}

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			try {
				final String deleteKey = "Delete-"+this.threadNumber;
				subject.put(deleteKey, "entry-to-be-deleted");
				
				String value = subject.get("global");
				Assertions.assertEquals("labolg", value);

				Thread.sleep(1500);
				
				// check that a cleanup has taken place
				value = subject.get(deleteKey);
				Assertions.assertNotEquals("entry-to-be-deleted", value);
				
				// verify that "global" wasn't touched
				value = subject.get("global");
				Assertions.assertEquals("labolg", value);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Assertions.fail("Thread was interrupted");
			}
			
			this.terminatedProperly = true;
		}

		public boolean isTerminatedProperly() {
			return terminatedProperly;
		}
		
	}
	
	@Test
	void testMassRun() throws InterruptedException {
		AutoRefreshingCacheMap<String, String> subject = new AutoRefreshingCacheMap<>("test", null, Duration.ofMillis(200), Duration.ofMillis(200), key -> {
			char[] source = key.toCharArray();
			char[] target = new char[source.length];
			for (int i = 0;i<source.length;i++) {
				target[source.length-i-1] = source[i];
			}
			return new String(target);
		});
		
		subject.setRefresherThreadWithIncreasedPriority(true);
		// Note that this also start the refresher thread immediately.
		// It helps keeping this unit test to stay stable.
		
		MassOperationTestThread[] threads = new MassOperationTestThread[10];
		for (int i = 0;i<threads.length;i++) {
			threads[i] = new MassOperationTestThread(subject, i);
			threads[i].start();
		}
		
		for (int i = 0;i<threads.length;i++) {
			threads[i].join(5000);
		}
		
		for (int i = 0;i<threads.length;i++) {
			Assertions.assertFalse(threads[i].isAlive());
			Assertions.assertTrue(threads[i].isTerminatedProperly()); // if this check fails, check on the console log!
		}
	}

	private int lockObjectStringWorksCalls = 0;
	
	private class LockObjectStringWorksThread extends Thread {
		private AutoRefreshingCacheMap<String, Mono<String>> subject;
		
		public LockObjectStringWorksThread(AutoRefreshingCacheMap<String, Mono<String>> subject) {
			super();
			this.subject = subject;
		}

		@Override
		public void run() {
			this.subject.get("abc");
		}
		
	}
	
	@Test
	void testLockObjectStringWorks() throws InterruptedException {
		AutoRefreshingCacheMap<String, Mono<String>> subject = new AutoRefreshingCacheMap<>("test",null, Duration.ofSeconds(10), Duration.ofSeconds(10), key -> {
			lockObjectStringWorksCalls++;
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Assertions.fail("Interrupted");
			}
			return Mono.just(key+"*");
		});
		
		LockObjectStringWorksThread[] threads = new LockObjectStringWorksThread[5];
		for (int i = 0;i<threads.length;i++) {
			threads[i] = new LockObjectStringWorksThread(subject);
			threads[i].start();
		}
		
		for (int i = 0;i<threads.length;i++) {
			threads[i].join(5000);
		}
		
		for (int i = 0;i<threads.length;i++) {
			Assertions.assertFalse(threads[i].isAlive());
		}
		
		// there shall only be one call to the loader function!
		Assertions.assertEquals(1, this.lockObjectStringWorksCalls);
	}
	
	private static class CompositeKey {
		private String a;
		private String b;
		
		public CompositeKey(String a, String b) {
			super();
			this.a = a;
			this.b = b;
		}
		/**
		 * @return the a
		 */
		public String getA() {
			return a;
		}
		/**
		 * @return the b
		 */
		public String getB() {
			return b;
		}
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((a == null) ? 0 : a.hashCode());
			result = prime * result + ((b == null) ? 0 : b.hashCode());
			return result;
		}
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CompositeKey other = (CompositeKey) obj;
			if (a == null) {
				if (other.a != null)
					return false;
			} else if (!a.equals(other.a)) {
				return false;
			}
			if (b == null) {
				if (other.b != null)
					return false;
			} else if (!b.equals(other.b)) {
				return false;
			}
			return true;
		}
	}
	
	private class LockObjectCompositeWorksThread extends Thread {
		private AutoRefreshingCacheMap<CompositeKey, Mono<String>> subject;
		
		public LockObjectCompositeWorksThread(AutoRefreshingCacheMap<CompositeKey, Mono<String>> subject) {
			this.subject = subject;
		}

		@Override
		public void run() {
			this.subject.get(new CompositeKey("a1", "b1"));
		}
		
	}
	
	private int lockObjectCompositeWorksCalls = 0;
	
	@Test
	void testLockObjectCompositeWorks() throws InterruptedException {
		log.info("Start of testLockObjectCompositeWorks");
		
		AutoRefreshingCacheMap<CompositeKey, Mono<String>> subject = new AutoRefreshingCacheMap<>("test",null, Duration.ofSeconds(10), Duration.ofSeconds(10), key -> {
			lockObjectCompositeWorksCalls++;
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Assertions.fail("Interrupted");
			}
			return Mono.just(key.getA() + key.getB() +"*");
		});
		
		LockObjectCompositeWorksThread[] threads = new LockObjectCompositeWorksThread[5];
		for (int i = 0;i<threads.length;i++) {
			threads[i] = new LockObjectCompositeWorksThread(subject);
			threads[i].start();
		}
		
		for (int i = 0;i<threads.length;i++) {
			threads[i].join(5000);
		}
		
		for (int i = 0;i<threads.length;i++) {
			Assertions.assertFalse(threads[i].isAlive());
		}
		
		// there shall only be one call to the loader function!
		Assertions.assertEquals(1, this.lockObjectCompositeWorksCalls);
	}
	
}
