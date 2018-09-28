package org.cloudfoundry.promregator.messagebus;

public interface MessageBusDestination {
	final static String PREFIX = "org.cloudfoundry.promregator.";
	
	/**
	 * name of an event to indicate that the discoverer has detected that an application
	 * seems no longer reachable.
	 */
	final static String DISCOVERER_INSTANCE_REMOVED = PREFIX + "discovery.instanceRemoved";
	
	/**
	 * name of an event to indicate that the state of an application or its Promregator-relevant
	 * properties have changed in such a way that a reload of the metadata is necessary.
	 */
	final static String CF_EVENT_APP_CHANGED = PREFIX + "cfevent.app.changed";
	
	/**
	 * name of an event to indicate that the route information (hostname, domain, etc.) of 
	 * an application has changed such that a reload is necessary.
	 */
	final static String CF_EVENT_ROUTE_TO_APP_CHANGED = PREFIX + "cfevent.route2app.changed";

	/**
	 * name of an event to indicate that the name of an application was changed 
	 * such that a reload is necessary (esp. on the discovery service)
	 */
	final static String CF_EVENT_APP_NAME_CHANGED = PREFIX + "cfevent.appname.changed";
	
	/**
	 * name of an event to indicate that a new application was created
	 */
	final static String CF_EVENT_APP_CREATED = PREFIX + "cfevent.app.created";
	
	/**
	 * name of an event to indicate that a new application was deleted
	 */
	final static String CF_EVENT_APP_DELETED = PREFIX + "cfevent.app.deleted";
	
	/**
	 * name of an event to indicate that a new space was created
	 */
	final static String CF_EVENT_SPACE_CREATED = PREFIX + "cfevent.space.created";
	
	/**
	 * name of an event to indicate that a space was deleted
	 */
	final static String CF_EVENT_SPACE_DELETED = PREFIX + "cfevent.space.deleted";
	
	/**
	 * name of an event to indicate that a space was changed
	 */
	final static String CF_EVENT_SPACE_CHANGED = PREFIX + "cfevent.space.changed";
	
	/**
	 * name of an event to indicate that a new org was created
	 */
	final static String CF_EVENT_ORG_CREATED = PREFIX + "cfevent.org.created";
	
	/**
	 * name of an event to indicate that an org was deleted
	 */
	final static String CF_EVENT_ORG_DELETED = PREFIX + "cfevent.org.deleted";
	
	/**
	 * name of an event to indicate that an org was changed
	 */
	final static String CF_EVENT_ORG_CHANGED = PREFIX + "cfevent.org.changed";
}
