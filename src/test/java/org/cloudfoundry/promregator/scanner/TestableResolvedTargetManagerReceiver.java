package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class TestableResolvedTargetManagerReceiver {
	private ResolvedTarget lastRt;
	
	@JmsListener(destination=MessageBusDestination.RESOLVEDTARGETMANAGER_RESOLVED_TARGET_REMOVED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void receiver(ResolvedTarget rt) {
		this.lastRt = rt;
	}

	/**
	 * @return the lastRt
	 */
	public ResolvedTarget getLastRt() {
		return lastRt;
	}
	
}