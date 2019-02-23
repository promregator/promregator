package org.cloudfoundry.promregator.cfaccessor;

public class CacheKeyAppsInSpace {

	private String orgId;
	private String spaceId;
	
	public CacheKeyAppsInSpace(String orgId, String spaceId) {
		super();
		this.orgId = orgId;
		this.spaceId = spaceId;
	}

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

	/* (non-Javadoc)
	 * Auto-generated using Eclipse
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((orgId == null) ? 0 : orgId.hashCode());
		result = prime * result + ((spaceId == null) ? 0 : spaceId.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * Auto-generated using Eclipse
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CacheKeyAppsInSpace other = (CacheKeyAppsInSpace) obj;
		if (orgId == null) {
			if (other.orgId != null)
				return false;
		} else if (!orgId.equals(other.orgId))
			return false;
		if (spaceId == null) {
			if (other.spaceId != null)
				return false;
		} else if (!spaceId.equals(other.spaceId))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "CacheKeySpace [orgId=" + orgId + ", spaceId=" + spaceId + "]";
	}
	
}
