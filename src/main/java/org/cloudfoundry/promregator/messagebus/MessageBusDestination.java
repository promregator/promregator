package org.cloudfoundry.promregator.messagebus;

public interface MessageBusDestination {
	public final static String PREFIX = "org.cloudfoundry.promregator.";
	
	public final static String DISCOVERER_INSTANCE_REMOVED = PREFIX + "instanceRemoved";
}
