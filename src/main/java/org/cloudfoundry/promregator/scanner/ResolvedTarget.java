package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.config.Target;

public class ResolvedTarget {
	private Target originalTarget;
	
	private String orgName;
	
	private String spaceName;
	
	private String applicationName;

	private String path;
	
	private String protocol;
	
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
			throw new Error(String.format("Invalid configuration: Target attempted to be configured with non-http(s) protocol: %s", protocol));
		}
	}

	public Target getOriginalTarget() {
		return originalTarget;
	}

	public void setOriginalTarget(Target originalTarget) {
		this.originalTarget = originalTarget;
	}
	
}
