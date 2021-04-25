package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.config.InvalidTargetProtocolSpecifiedError;
import org.cloudfoundry.promregator.config.Target;

public class ResolvedTarget {
	private Target originalTarget;
	
	private String orgName;
	
	private String spaceName;
	
	private String applicationName;

	private String path;
	
	private String protocol;

	private String applicationId;

	private Boolean kubernetesAnnotations = false;
	
	public ResolvedTarget() {
		super();
	}
	
	public ResolvedTarget(Target configTarget) {
		this.originalTarget = configTarget;
		this.orgName = configTarget.getOrgName();
		this.spaceName = configTarget.getSpaceName();
		this.applicationName = configTarget.getApplicationName();
		this.path = configTarget.getPath();
		this.protocol = configTarget.getProtocol();
		this.kubernetesAnnotations = configTarget.getKubernetesAnnotations();
	}
	
	public String getOrgName() {
		return orgName;
	}

	public void setOrgName(String orgName) {
		this.orgName = orgName;
	}

	public String getSpaceName() {
		return spaceName;
	}

	public void setSpaceName(String spaceName) {
		this.spaceName = spaceName;
	}

	public String getApplicationName() {
		return applicationName;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public String getPath() {
		if (this.path == null) {
			return "/metrics";
		}
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Boolean getKubernetesAnnotations() {
		return kubernetesAnnotations;
	}

	public void setKubernetesAnnotations(Boolean kubernetesAnnotations) {
		this.kubernetesAnnotations = kubernetesAnnotations != null ? kubernetesAnnotations : false;
	}

	public String getProtocol() {
		if (this.protocol == null) {
			return "https";
		}
		return protocol;
	}

	public void setProtocol(String protocol) {
		if ("http".equals(protocol) || "https".equals(protocol)) {
			this.protocol = protocol;
		} else {
			throw new InvalidTargetProtocolSpecifiedError(String.format("Invalid configuration: Target attempted to be configured with non-http(s) protocol: %s", protocol));
		}
	}

	public Target getOriginalTarget() {
		return originalTarget;
	}

	public void setOriginalTarget(Target originalTarget) {
		this.originalTarget = originalTarget;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((applicationName == null) ? 0 : applicationName.hashCode());
		result = prime * result + ((orgName == null) ? 0 : orgName.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((kubernetesAnnotations == null) ? 0 : kubernetesAnnotations.hashCode());
		result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
		result = prime * result + ((spaceName == null) ? 0 : spaceName.hashCode());
		result = prime * result + ((applicationId == null) ? 0 : applicationId.hashCode());
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
		ResolvedTarget other = (ResolvedTarget) obj;
		if (applicationName == null) {
			if (other.applicationName != null) {
				return false;
			}
		} else if (!applicationName.equals(other.applicationName)) {
			return false;
		}
		if (applicationId == null) {
			if (other.applicationId != null) {
				return false;
			}
		} else if (!applicationId.equals(other.applicationId)) {
			return false;
		}
		if (orgName == null) {
			if (other.orgName != null) {
				return false;
			}
		} else if (!orgName.equals(other.orgName)) {
			return false;
		}
		if (path == null) {
			if (other.path != null) {
				return false;
			}
		} else if (!path.equals(other.path)) {
			return false;
		}
		if (kubernetesAnnotations == null) {
			if (other.kubernetesAnnotations != null) {
				return false;
			}
		} else if (!kubernetesAnnotations.equals(other.kubernetesAnnotations)) {
			return false;
		}
		if (protocol == null) {
			if (other.protocol != null) {
				return false;
			}
		} else if (!protocol.equals(other.protocol)) {
			return false;
		}
		if (spaceName == null) {
			if (other.spaceName != null) {
				return false;
			}
		} else if (!spaceName.equals(other.spaceName)) {
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ResolvedTarget [orgName=");
		builder.append(orgName);
		builder.append(", spaceName=");
		builder.append(spaceName);
		builder.append(", applicationName=");
		builder.append(applicationName);
		builder.append(", applicationId=");
		builder.append(applicationId);
		builder.append(", path=");
		builder.append(path);
		builder.append(", kuberntesAnnotations=");
		builder.append(kubernetesAnnotations.toString());
		builder.append(", protocol=");
		builder.append(protocol);
		builder.append("]");
		return builder.toString();
	}
}
