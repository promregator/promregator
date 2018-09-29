package org.cloudfoundry.promregator.discovery;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;

/**
 * An instance provides a mapping from a target (provided by configuration)
 * to an exact descriptor consisting of the Access URL and the instance identifier, 
 * which can be used for scraping.
 */
public abstract class Instance {
	protected String instanceId;
	protected String accessUrl;
	
	public Instance(String instanceId, String accessUrl) {
		super();
		this.instanceId = instanceId;
		this.accessUrl = accessUrl;
	}

	public abstract String getOrgName();
	public abstract String getSpaceName();
	public abstract String getApplicationName();
	public abstract String getPath();
	
	public abstract AuthenticationEnricher getAuthenticationEnricher();
	
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

	/**
	 * @param instanceId the instanceId to set
	 */
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	/**
	 * @param accessUrl the accessUrl to set
	 */
	public void setAccessUrl(String accessUrl) {
		this.accessUrl = accessUrl;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((accessUrl == null) ? 0 : accessUrl.hashCode());
		result = prime * result + ((instanceId == null) ? 0 : instanceId.hashCode());
		return result;
	}

	/* (non-Javadoc)
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
		Instance other = (Instance) obj;
		if (accessUrl == null) {
			if (other.accessUrl != null)
				return false;
		} else if (!accessUrl.equals(other.accessUrl))
			return false;
		if (instanceId == null) {
			if (other.instanceId != null)
				return false;
		} else if (!instanceId.equals(other.instanceId))
			return false;
		return true;
	}


}
