package org.cloudfoundry.promregator.discovery;

public class InstanceKey {
	private String orgName;
	private String spaceName;
	private String appName;
	
	private String instanceId;

	/* NB: Mandatory for JSON deserialization! */
	public InstanceKey() {
		super();
	}
	
	public InstanceKey(String orgName, String spaceName, String appName, String instanceId) {
		super();
		this.orgName = orgName;
		this.spaceName = spaceName;
		this.appName = appName;
		this.instanceId = instanceId;
	}

	/**
	 * @return the orgName
	 */
	public String getOrgName() {
		return orgName;
	}

	/**
	 * @return the spaceName
	 */
	public String getSpaceName() {
		return spaceName;
	}

	/**
	 * @return the appName
	 */
	public String getAppName() {
		return appName;
	}

	/**
	 * @return the instanceId
	 */
	public String getInstanceId() {
		return instanceId;
	}

	/**
	 * @param orgName the orgName to set
	 */
	public void setOrgName(String orgName) {
		this.orgName = orgName;
	}

	/**
	 * @param spaceName the spaceName to set
	 */
	public void setSpaceName(String spaceName) {
		this.spaceName = spaceName;
	}

	/**
	 * @param appName the appName to set
	 */
	public void setAppName(String appName) {
		this.appName = appName;
	}

	/**
	 * @param instanceId the instanceId to set
	 */
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
	
	
}
