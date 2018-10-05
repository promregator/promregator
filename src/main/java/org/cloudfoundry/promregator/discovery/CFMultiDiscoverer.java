package org.cloudfoundry.promregator.discovery;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.validation.constraints.Null;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CFMultiDiscoverer implements CFDiscoverer {
	private static final Logger log = Logger.getLogger(CFMultiDiscoverer.class);

	
	@Autowired
	private ConfigurationTargetCFDiscoverer configurationTargetCFDiscoverer;

	@Autowired
	private UPSBasedCFDiscoverer upsBasedCFDiscoverer;
	
	@Autowired
	private JmsTemplate jmsTemplate;
	
	@Autowired
	private Clock clock;
	
	private Map<Instance, Instant> instanceExpiryMap = new ConcurrentHashMap<>();
	
	@Value("${promregator.discoverer.timeout:600}")
	private int expiryTimeout;
	
	@Null
	public List<Instance> discover(@Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) {
		
		Flux<Instance> configurationTargetDiscoveryFlux = Mono.fromCallable( () -> this.configurationTargetCFDiscoverer.discover(applicationIdFilter, instanceFilter))
		.subscribeOn(Schedulers.elastic())
		.elapsed()
		.map(tuple -> {
			Long time = tuple.getT1();
			log.debug(String.format("Configuration-Target-based discovery returned after %dms", time));
			return tuple.getT2();
		})
		.flatMapMany(list -> Flux.fromIterable(list))
		.log(CFDiscoverer.class.getCanonicalName()+".configurationTargetDiscovery")
		.doOnError(e -> {
			log.error("Error while performing configuration-target-based discovery", e);
		}).onErrorResume(__ -> Flux.empty());
		
		Flux<Instance> upsDiscoveryFlux = Mono.fromCallable( () -> this.upsBasedCFDiscoverer.discover(applicationIdFilter, instanceFilter))
		.subscribeOn(Schedulers.elastic())
		.elapsed()
		.map(tuple -> {
			Long time = tuple.getT1();
			log.debug(String.format("UPS-based discovery returned after %dms", time));
			return tuple.getT2();
		})
		.flatMapMany(list -> Flux.fromIterable(list))
		.log(CFDiscoverer.class.getCanonicalName()+".upsBasedDiscovery")
		.doOnError(e -> {
			log.error("Error while performing UPS-based discovery", e);
		}).onErrorResume(__ -> Flux.empty());

		List<Instance> instanceList = Flux.merge(configurationTargetDiscoveryFlux, upsDiscoveryFlux)
		.collectList().block();
		
		if (instanceList != null) {
			// ensure that the instances are registered / touched properly
			for (Instance instance : instanceList) {
				this.registerInstance(instance);
			}
		}
		
		return instanceList;
	}
	
	private void registerInstance(Instance instance) {
		Instant timeout = nextTimeout();
		this.instanceExpiryMap.put(instance, timeout);
		// NB: If already in the map, then the timeout is overwritten => refreshing/touching
	}
	
	/**
	 * checks whether a given instance is known at this Discoverer (or may already have expired)
	 * @param instance the instance which shall be checked
	 * @return <code>true</code> if the instance is registered; <code>false</code> otherwise. Note that the response is independent whether the validity of the instance has already expired or not!
	 */
	public boolean isInstanceRegistered(Instance instance) {
		return this.instanceExpiryMap.containsKey(instance);
	}

	private Instant nextTimeout() {
		Instant timeout = Instant.now(this.clock).plus(this.expiryTimeout, ChronoUnit.SECONDS);
		
		return timeout;
	}
	
	/**
	 * checks the list of registered Instances to be expired.
	 * This method is automatically called by the Spring framework in regular intervals asynchronously.
	 */
	@Scheduled(fixedDelay=60*1000)
	public void cleanup() {
		Instant now = Instant.now(this.clock);
		
		for (Iterator<Entry<Instance, Instant>> it = this.instanceExpiryMap.entrySet().iterator(); it.hasNext();) {
			Entry<Instance, Instant> entry = it.next();
			
			if (entry.getValue().isAfter(now)) {
				// not ripe yet; skip
				continue;
			}
			
			log.info(String.format("Instance %s has timed out; cleaning up", entry.getKey()));
			
			// broadcast event to JMS topic, that the instance is to be deleted
			this.jmsTemplate.convertAndSend(MessageBusDestination.DISCOVERER_INSTANCE_REMOVED, entry.getKey());
			
			it.remove();
		}
	}
	
	public void setClock(Clock newClock) {
		this.clock = newClock;
	}
}
