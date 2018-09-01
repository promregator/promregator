package org.cloudfoundry.promregator.cfaccessor;

public class CacheKeySpace {

	private String orgId;
	private String spaceName;
	
	public CacheKeySpace(String orgId, String spaceName) {
		super();
		this.orgId = orgId;
		this.spaceName = spaceName;
	}

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

	/* (non-Javadoc)
	 * Auto-generated using Eclipse
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((orgId == null) ? 0 : orgId.hashCode());
		result = prime * result + ((spaceName == null) ? 0 : spaceName.hashCode());
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
		CacheKeySpace other = (CacheKeySpace) obj;
		if (orgId == null) {
			if (other.orgId != null)
				return false;
		} else if (!orgId.equals(other.orgId))
			return false;
		if (spaceName == null) {
			if (other.spaceName != null)
				return false;
		} else if (!spaceName.equals(other.spaceName))
			return false;
		return true;
	}
	
	
}
