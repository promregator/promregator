package org.cloudfoundry.promregator.scanner;

/**
 * An instance provides a mapping from a target (provided by configuration)
 * to an exact descriptor consisting of the Access URL and the instance identifier, 
 * which can be used for scraping.
 */
public class Instance {
	private ResolvedTarget target;
	private String instanceId;
	private String accessUrl;
	
	public Instance(ResolvedTarget target, String instanceId, String accessUrl) {
		super();
		this.target = target;
		this.instanceId = instanceId;
		this.accessUrl = accessUrl;
	}

	public ResolvedTarget getTarget() {
		return target;
	}

	public String getInstanceId() {
		return instanceId;
	}

	public String getAccessUrl() {
		return accessUrl;
	}
	
	public String getInstanceNumber() {
		String[] parts = this.instanceId.split(":");
		return parts[1];
	}

	public String getApplicationId() {
		String[] parts = this.instanceId.split(":");
		return parts[0];
	}
}
