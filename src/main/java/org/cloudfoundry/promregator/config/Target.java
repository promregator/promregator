package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;

public class Target implements Cloneable {
	private static final Logger log = Logger.getLogger(Target.class);
	
	private String orgName;
	
	private String orgRegex;
	
	private String spaceName;
	
	private String spaceRegex;
	
	private String applicationName;
	
	private String applicationRegex;

	private String path;
	
	private String protocol;
	
	private String authenticatorId;
	
	private List<String> preferredRouteRegex;
	private List<Pattern> cachedPreferredRouteRegexPattern;
	
	public Target() {
		super();
	}
	
	/**
	 * creates a copy of an existing Target
	 * @param source the template which shall be used for copying
	 */
	public Target(Target source) {
		this.orgName = source.orgName;
		this.orgRegex = source.orgRegex;
		this.spaceName = source.spaceName;
		this.spaceRegex = source.spaceRegex;
		this.applicationName = source.applicationName;
		this.applicationRegex = source.applicationRegex;
		this.path = source.path;
		this.protocol = source.protocol;
		this.authenticatorId = source.authenticatorId;
		
		if (source.preferredRouteRegex == null) {
			this.preferredRouteRegex = new ArrayList<>(0);
		} else {
			this.preferredRouteRegex = new ArrayList<>(source.preferredRouteRegex);
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	protected Object clone() throws CloneNotSupportedException {
		return new Target(this);
	}

	
	public String getOrgName() {
		return orgName;
	}

	public void setOrgName(String orgName) {
		this.orgName = orgName;
	}
	

	public String getOrgRegex() {
		return orgRegex;
	}

	public void setOrgRegex(String orgRegex) {
		this.orgRegex = orgRegex;
	}

	public String getSpaceName() {
		return spaceName;
	}

	public void setSpaceName(String spaceName) {
		this.spaceName = spaceName;
	}

	public String getSpaceRegex() {
		return spaceRegex;
	}

	public void setSpaceRegex(String spaceRegex) {
		this.spaceRegex = spaceRegex;
	}

	public String getApplicationName() {
		return applicationName;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public String getApplicationRegex() {
		return applicationRegex;
	}
	
	public void setApplicationRegex(String applicationRegex) {
		this.applicationRegex = applicationRegex;
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

	public String getAuthenticatorId() {
		return authenticatorId;
	}

	public void setAuthenticatorId(String authenticatorId) {
		this.authenticatorId = authenticatorId;
	}

	/**
	 * @return the preferredRouteRegex
	 */
	public List<String> getPreferredRouteRegex() {
		if (this.preferredRouteRegex == null) {
			return null;
		}
		
		return new ArrayList<>(preferredRouteRegex);
	}

	/**
	 * @param preferredRouteRegex the preferredRouteRegex to set
	 */
	public void setPreferredRouteRegex(List<String> preferredRouteRegex) {
		this.preferredRouteRegex = preferredRouteRegex;
		this.cachedPreferredRouteRegexPattern = null; // reset cache
	}

	public List<Pattern> getPreferredRouteRegexPatterns() {
		if (this.cachedPreferredRouteRegexPattern != null) {
			return this.cachedPreferredRouteRegexPattern;
		}
		
		List<String> regexStringList = this.getPreferredRouteRegex();
		if (regexStringList == null) {
			return null;
		}
		
		List<Pattern> patterns = new ArrayList<>(regexStringList.size());
		for (String routeRegex : regexStringList) {
			try {
				Pattern pattern = Pattern.compile(routeRegex);
				patterns.add(pattern);
			} catch (PatternSyntaxException e) {
				log.warn(String.format("Invalid preferredRouteRegex '%s' detected. Fix your configuration; until then, the regex will be ignored", routeRegex), e);
				continue;
			}
		}
		
		this.cachedPreferredRouteRegexPattern = patterns;
		
		return this.cachedPreferredRouteRegexPattern;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Target [orgName=");
		builder.append(orgName);
		builder.append(", orgRegex=");
		builder.append(orgRegex);
		builder.append(", spaceName=");
		builder.append(spaceName);
		builder.append(", spaceRegex=");
		builder.append(spaceRegex);
		builder.append(", applicationName=");
		builder.append(applicationName);
		builder.append(", applicationRegex=");
		builder.append(applicationRegex);
		builder.append(", path=");
		builder.append(path);
		builder.append(", protocol=");
		builder.append(protocol);
		builder.append(", authenticatorId=");
		builder.append(authenticatorId);
		builder.append(", preferredRouteRegex=");
		builder.append(preferredRouteRegex);
		builder.append("]");
		return builder.toString();
	}


	
}
