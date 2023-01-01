package org.cloudfoundry.promregator.messagebus;

public final class MessageBusTopic {
	private MessageBusTopic() {
		throw new IllegalStateException("Should never be called");
	}
	
	public static final String PREFIX = "org.cloudfoundry.promregator.";
	
	public static final String DISCOVERER_INSTANCE_REMOVED = PREFIX + "instanceRemoved";
}
