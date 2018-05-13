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

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((accessUrl == null) ? 0 : accessUrl.hashCode());
		result = prime * result + ((instanceId == null) ? 0 : instanceId.hashCode());
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Instance other = (Instance) obj;
		if (accessUrl == null) {
			if (other.accessUrl != null) {
				return false;
			}
		} else if (!accessUrl.equals(other.accessUrl)) {
			return false;
		}
		if (instanceId == null) {
			if (other.instanceId != null) {
				return false;
			}
		} else if (!instanceId.equals(other.instanceId)) {
			return false;
		}
		if (target == null) {
			if (other.target != null) {
				return false;
			}
		} else if (!target.equals(other.target)) {
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	// TODO requires unit test coverage
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Instance [target=");
		builder.append(target);
		builder.append(", instanceId=");
		builder.append(instanceId);
		builder.append(", accessUrl=");
		builder.append(accessUrl);
		builder.append("]");
		return builder.toString();
	}
	
}
