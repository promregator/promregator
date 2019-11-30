package org.cloudfoundry.promregator.messagebus;

public interface MessageBusDestination {
	static final String PREFIX = "org.cloudfoundry.promregator.";
	
	static final String DISCOVERER_INSTANCE_REMOVED = PREFIX + "instanceRemoved";
}
