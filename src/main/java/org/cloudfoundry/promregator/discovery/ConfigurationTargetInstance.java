package org.cloudfoundry.promregator.discovery;

import org.cloudfoundry.promregator.scanner.ResolvedTarget;

public class ConfigurationTargetInstance extends Instance {
	private ResolvedTarget target;

	public ConfigurationTargetInstance(ResolvedTarget target, String instanceId, String accessUrl) {
		super(instanceId, accessUrl);
		this.target = target;
	}

	public ResolvedTarget getTarget() {
		return target;
	}

	/**
	 * @param target the target to set
	 */
	public void setTarget(ResolvedTarget target) {
		this.target = target;
	}
	
	@Override
	public String getOrgName() {
		return this.target.getOrgName();
	}

	@Override
	public String getSpaceName() {
		return this.target.getSpaceName();
	}

	@Override
	public String getApplicationName() {
		return this.target.getApplicationName();
	}
	
	@Override
	public String getPath() {
		return this.target.getPath();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConfigurationTargetInstance other = (ConfigurationTargetInstance) obj;
		if (target == null) {
			if (other.target != null)
				return false;
		} else if (!target.equals(other.target))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ConfigurationTargetInstance [target=");
		builder.append(target);
		builder.append(", instanceId=");
		builder.append(instanceId);
		builder.append(", accessUrl=");
		builder.append(accessUrl);
		builder.append("]");
		return builder.toString();
	}
	
}
