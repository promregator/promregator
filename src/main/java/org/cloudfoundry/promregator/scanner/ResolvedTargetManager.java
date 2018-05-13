package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ResolvedTargetManager {
	private static final Logger log = Logger.getLogger(ResolvedTargetManager.class);
	
	private Map<ResolvedTarget, Instant> resolvedTargetExpiryMap = new ConcurrentHashMap<ResolvedTarget, Instant>();
	
	@Value("${cf.cache.timeout.resolvedTarget:300}")
	private int expiryTimeout;
	
	@Autowired
	private JmsTemplate jmsTemplate;
	
	private Clock clock;
	
	public ResolvedTargetManager(Clock clock) {
		this.clock = clock;
	}
	
	public void registerResolvedTarget(ResolvedTarget rt) {
		Instant timeout = nextTimeout();
		this.resolvedTargetExpiryMap.put(rt, timeout);
		// NB: If already in the map, then the timeout is overwritten => refreshing/touching
	}

	private Instant nextTimeout() {
		Instant timeout = Instant.now(this.clock).plus(this.expiryTimeout, ChronoUnit.SECONDS);
		return timeout;
	}
	
	public void deregisterResolvedTarget(ResolvedTarget rt) {
		// broadcast event to JMS topic, that the ResolvedTarget is to be deleted
		this.jmsTemplate.convertAndSend(MessageBusDestination.RESOLVEDTARGETMANAGER_RESOLVED_TARGET_REMOVED, rt);
		
		this.resolvedTargetExpiryMap.remove(rt);
	}
	
	@Scheduled(fixedDelay=60*1000)
	public void cleanup() {
		Instant now = Instant.now(this.clock);
		
		for (Iterator<Entry<ResolvedTarget, Instant>> it = this.resolvedTargetExpiryMap.entrySet().iterator(); it.hasNext();) {
			Entry<ResolvedTarget, Instant> entry = it.next();
			
			if (entry.getValue().isAfter(now)) {
				// not ripe yet; skip
				continue;
			}
			
			log.info(String.format("Resolved Target %s has timed out; cleaning up", entry.getKey()));
			
			// broadcast event to JMS topic, that the ResolvedTarget is to be deleted
			this.jmsTemplate.convertAndSend(MessageBusDestination.RESOLVEDTARGETMANAGER_RESOLVED_TARGET_REMOVED, entry.getKey());
			
			it.remove();
		}
	}
	
	public void setClock(Clock clock) {
		this.clock = clock;
	}
	
	public boolean isEmpty() {
		return this.resolvedTargetExpiryMap.isEmpty();
	}
}
