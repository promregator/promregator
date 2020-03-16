package org.cloudfoundry.promregator.discovery;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.validation.constraints.Null;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public class CFMultiDiscoverer implements CFDiscoverer {
	private static final Logger log = LoggerFactory.getLogger(CFMultiDiscoverer.class);
	
	private final TargetResolver targetResolver;
	private final AppInstanceScanner appInstanceScanner;
	private final PromregatorConfiguration promregatorConfiguration;
	private final JmsTemplate jmsTemplate;
	private final Clock clock;
	
	private Map<Instance, Instant> instanceExpiryMap = new ConcurrentHashMap<>();

	public CFMultiDiscoverer(TargetResolver targetResolver, AppInstanceScanner appInstanceScanner, PromregatorConfiguration promregatorConfiguration, JmsTemplate jmsTemplate, Clock clock) {
		this.targetResolver = targetResolver;
		this.appInstanceScanner = appInstanceScanner;
		this.promregatorConfiguration = promregatorConfiguration;
		this.jmsTemplate = jmsTemplate;
		this.clock = clock;
	}

	/**
	 * performs the discovery based on the configured set of targets in the configuration, (pre-)filtering the returned set applying the filter criteria supplied.
	 * The instances discovered are automatically registered at this Discoverer
	 * @param applicationIdFilter the (pre-)filter based on ApplicationIds, allowing to early filter the list of instances to discover
	 * @param instanceFilter the (pre-)filter based on the Instance instance, allowing to filter the lost if instances to discover
	 * @return the list of Instances which were discovered (and registered).
	 */
	@Null
	public List<Instance> discover(@Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) {
		log.debug(String.format("We have %d targets configured", this.promregatorConfiguration.getTargets().size()));
		
		List<ResolvedTarget> resolvedTargets = this.targetResolver.resolveTargets(this.promregatorConfiguration.getTargets());
		if (resolvedTargets == null) {
			log.warn("Target resolved was unable to resolve configured targets");
			return Collections.emptyList();
		}
		log.debug(String.format("Raw list contains %d resolved targets", resolvedTargets.size()));
		
		List<Instance> instanceList = this.appInstanceScanner.determineInstancesFromTargets(resolvedTargets, applicationIdFilter, instanceFilter);
		if (instanceList == null) {
			log.warn("Instance Scanner unable to determine instances from provided targets");
			return Collections.emptyList();
		}
		log.debug(String.format("Raw list contains %d instances", instanceList.size()));

		// ensure that the instances are registered / touched properly
		for (Instance instance : instanceList) {
			this.registerInstance(instance);
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
		return Instant.now(this.clock).plus(this.promregatorConfiguration.getDiscoverer().getTimeout(), ChronoUnit.SECONDS);
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
	
}
