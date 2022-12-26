package org.cloudfoundry.promregator.messagebus;

public interface MessageBusTopic {
	String PREFIX = "org.cloudfoundry.promregator.";
	
	String DISCOVERER_INSTANCE_REMOVED = PREFIX + "instanceRemoved";
}
