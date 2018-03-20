package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.config.Target;

/**
 * An instance provides a mapping from a target (provided by configuration)
 * to an exact descriptor consisting of the Access URL and the instance identifier, 
 * which can be used for scraping.
 */
public class Instance {
	private Target target;
	private String instanceId;
	private String accessUrl;
	
	public Instance(Target target, String instanceId, String accessUrl) {
		super();
		this.target = target;
		this.instanceId = instanceId;
		this.accessUrl = accessUrl;
	}

	public Target getTarget() {
		return target;
	}

	public String getInstanceId() {
		return instanceId;
	}

	public String getAccessUrl() {
		return accessUrl;
	}
	
}
