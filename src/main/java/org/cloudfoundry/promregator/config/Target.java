package org.cloudfoundry.promregator.config;

public class Target implements Cloneable {
	private String orgName;

	private String spaceName;

	private String applicationName;

	private String path;

	private String protocol;

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
		if (this.path == null)
			return "/metrics";

		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getProtocol() {
		if (this.protocol == null)
			return "https";

		return protocol;
	}

	public void setProtocol(String protocol) {
		if ("http".equals(protocol) || "https".equals(protocol)) {
			this.protocol = protocol;
		} else {
			throw new Error(String.format(
					"Invalid configuration: Target attempted to be configured with non-http(s) protocol: %s",
					protocol));
		}
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		Target clone = (Target) super.clone();
		clone.orgName = this.orgName;
		clone.spaceName = this.spaceName;
		clone.applicationName = this.applicationName;
		clone.path = this.path;
		clone.protocol = this.protocol;
		
		return clone;
	}

}
