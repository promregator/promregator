package org.cloudfoundry.promregator.messagebus;

public interface MessageBusDestination {
	final static String PREFIX = "org.cloudfoundry.promregator.";
	
	final static String DISCOVERER_INSTANCE_REMOVED = PREFIX + "instanceRemoved";
}
