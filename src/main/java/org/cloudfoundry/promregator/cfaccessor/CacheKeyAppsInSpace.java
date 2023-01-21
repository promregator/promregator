package org.cloudfoundry.promregator.cfaccessor;

public record CacheKeyAppsInSpace(String orgId, String spaceId) {

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
}
