package org.cloudfoundry.promregator.cfaccessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import reactor.core.publisher.Mono;

public abstract class RequestAggregator<K, V> {
	private static final Logger log = LoggerFactory.getLogger(RequestAggregator.class);

	private static record QueueItem<K, V> (K requestItem, CompletableFuture<V> future) {}
	
	public enum Type {
		ROUTE("route"),
		PROCESS("process"),
		OTHER("other"); // used for unit testing
		
		private String typeName;
		
		private Type(String typeName) {
			this.typeName = typeName;
		}

		/**
		 * @return the typeName
		 */
		public String getTypeName() {
			return typeName;
		}
	}
	
	private ConcurrentLinkedDeque<QueueItem<K, V>> queue = new ConcurrentLinkedDeque<>();
	
	private Processor processor;
	
	private int checkIntervalInMillis;
	private int maxBlockSize;
	
	private class Processor extends Thread {
		
		private final Logger log = LoggerFactory.getLogger(Processor.class);

		public Processor(Class<K> typeOfK, Class<V> typeOfV) {
			super("Processor for ReactiveCFAccesor requests %s -> %s".formatted(typeOfK.toString(), typeOfV.toString()));
		}
		
		private boolean shouldRun = true;
		
		@Override
		public void run() {
			
			while (shouldRun) {
				
				try {
					Thread.sleep(checkIntervalInMillis);
				} catch (InterruptedException e) {
					log.info("Processor was interrupted", e);
					Thread.currentThread().interrupt();
				}
				
				if (queue.isEmpty()) {
					continue;
				}
				
				log.debug("Woke up with {} items in the queue", queue.size());
				
				// there is something in the queue which needs to be handled now
				final HashSet<K> blockSet = new HashSet<>(maxBlockSize);
				final HashMap<K, CompletableFuture<V>> map = new HashMap<>();
				
				while (blockSet.size() < maxBlockSize && !queue.isEmpty()) {
					final QueueItem<K,V> queueItem = queue.poll();
					map.put(queueItem.requestItem(), queueItem.future());
					
					blockSet.add(queueItem.requestItem());
				}
				
				log.debug("Sending a request with a block of {} items", blockSet.size());
				final Mono<V> responseMono = sendRequest(blockSet);
				
				responseMono.doOnNext(response -> {
					log.debug("Received response {}", response);
					Map<K, V> responseMap = determineMapOfResponses(response);
					
					map.entrySet().forEach(entry -> {
						K key = entry.getKey();
						CompletableFuture<V> future = entry.getValue();
						
						V list = responseMap.get(key);
						log.debug("Resolving {} with {}", key, list);
						future.complete(list); // which may be null
					});
				})
				.doOnError(t -> {
					log.info("Exception was raised during retrieving the response; propagating exception to all requestors", t);
					map.values().forEach(e -> e.completeExceptionally(t));
				}).onErrorStop()
				.subscribe();
				
			}
			
		}
		
	}
	
	protected RequestAggregator(Type type, InternalMetrics internalMetrics, Class<K> typeOfK, Class<V> typeOfV, int checkIntervalInMillis, int maxBlockSize) {
		this.checkIntervalInMillis = checkIntervalInMillis;
		this.maxBlockSize = maxBlockSize;
		
		Assert.isTrue(checkIntervalInMillis > 0, "Check Interval must not be negative or zero");
		Assert.isTrue(maxBlockSize > 0, "BlockSize must not be negative or zero");
		
		if (internalMetrics != null) {
			switch (type) {
			case ROUTE: 
				internalMetrics.registerDequeRouteSizeFunction(() -> (double) queue.size());
				break;
			case PROCESS: 
				internalMetrics.registerDequeProcessSizeFunction(() -> (double) queue.size());
				break;
			case OTHER:
				break; // ignore
			default:
				throw new UnsupportedOperationException("Unkown RequestAggregator Type");
			}
			
		}
		
		this.processor = new Processor(typeOfK, typeOfV);
		this.processor.start();
	}
	
	public void addToQueue(K item, CompletableFuture<V> future) {
		log.debug("Adding item {}", item);
		this.queue.add(new QueueItem<>(item, future));
	}
	
	public void stop() {
		this.processor.shouldRun = false;
	}
	
	/**
	 * sends a request based on a set of requests in the block
	 * @param block the block of requests which shall be sent
	 * @return a Mono with the response of the request sent
	 */
	protected abstract Mono<V> sendRequest(Set<K> block);
	
	/**
	 * converts the response into a map, whose key is the request identifier of the block. The values are
	 * responses to that identifier.
	 * @param response the response retrieve which shall be used for determination
	 * @return the map containing values as specified above.
	 */
	protected abstract Map<K, V> determineMapOfResponses(V response);
	
}
