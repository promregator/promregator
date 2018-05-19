package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class TestableResolvedTargetManagerReceiver {
	private Instance lastInstance;
	
	@JmsListener(destination=MessageBusDestination.DISCOVERER_INSTANCE_REMOVED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void receiver(Instance instance) {
		this.lastInstance = instance;
	}

	/**
	 * @return the lastInstance
	 */
	public Instance getLastRt() {
		return lastInstance;
	}
	
}