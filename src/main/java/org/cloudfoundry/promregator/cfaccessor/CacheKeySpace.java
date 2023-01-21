package org.cloudfoundry.promregator.cfaccessor;

public record CacheKeySpace(String orgId, String spaceName) {

	/**
	 * @return the orgId
	 */
	public String getOrgId() {
		return orgId;
	}

	/**
	 * @return the spaceName
	 */
	public String getSpaceName() {
		return spaceName;
	}

}
