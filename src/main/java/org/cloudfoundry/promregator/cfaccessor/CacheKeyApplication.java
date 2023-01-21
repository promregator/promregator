package org.cloudfoundry.promregator.cfaccessor;

public record CacheKeyApplication (String orgId, String spaceId, String applicationName) {

	/**
	 * @return the orgId
	 */
	public String getOrgId() {
		return orgId;
	}
	/**
	 * @return the spaceId
	 */
	public String getSpaceId() {
		return spaceId;
	}
	/**
	 * @return the applicationName
	 */
	public String getApplicationName() {
		return applicationName;
	}
}
