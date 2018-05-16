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
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CFDiscoverer {
	private static final Logger log = Logger.getLogger(CFDiscoverer.class);
	
	@Autowired
	private TargetResolver targetResolver;
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;

	@Autowired
	private JmsTemplate jmsTemplate;

	@Autowired
	private Clock clock;
	
	private Map<Instance, Instant> instanceExpiryMap = new ConcurrentHashMap<>();
	
	@Value("${cf.cache.timeout.instance:300}")
	// TODO requires mentioning in the documentation
	private int expiryTimeout;
	
	public List<Instance> discover(@Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) {
		log.debug(String.format("We have %d targets configured", this.promregatorConfiguration.getTargets().size()));
		
		List<ResolvedTarget> resolvedTargets = this.targetResolver.resolveTargets(this.promregatorConfiguration.getTargets());
		log.debug(String.format("Raw list contains %d resolved targets", resolvedTargets.size()));
		
		List<Instance> instanceList = this.appInstanceScanner.determineInstancesFromTargets(resolvedTargets, applicationIdFilter, instanceFilter);
		log.debug(String.format("Raw list contains %d instances", instanceList.size()));

		// ensure that the instances are registered / touched properly
		// TODO requires unit test coverage that the instance list really properly touches the timestamps ==> TestableCFDiscoverer
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

	private Instant nextTimeout() {
		Instant timeout = Instant.now(this.clock).plus(this.expiryTimeout, ChronoUnit.SECONDS);
		return timeout;
	}
	
	@Scheduled(fixedDelay=60*1000)
	// TODO requires unit test that really the marked instances are removed (and the message is sent through the bus)
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
