package org.cloudfoundry.promregator.messagebus;

public interface MessageBusDestination {
	public final static String PREFIX = "org.cloudfoundry.promregator.";
	
	public final static String RESOLVEDTARGETMANAGER_RESOLVED_TARGET_REMOVED = PREFIX + "resolvedTargetRemoved";
}
