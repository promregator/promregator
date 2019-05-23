package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

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

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((applicationName == null) ? 0 : applicationName.hashCode());
		result = prime * result + ((applicationRegex == null) ? 0 : applicationRegex.hashCode());
		result = prime * result + ((authenticatorId == null) ? 0 : authenticatorId.hashCode());
		result = prime * result + ((orgName == null) ? 0 : orgName.hashCode());
		result = prime * result + ((orgRegex == null) ? 0 : orgRegex.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((preferredRouteRegex == null) ? 0 : preferredRouteRegex.hashCode());
		result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
		result = prime * result + ((spaceName == null) ? 0 : spaceName.hashCode());
		result = prime * result + ((spaceRegex == null) ? 0 : spaceRegex.hashCode());
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
		Target other = (Target) obj;
		if (applicationName == null) {
			if (other.applicationName != null) {
				return false;
			}
		} else if (!applicationName.equals(other.applicationName)) {
			return false;
		}
		if (applicationRegex == null) {
			if (other.applicationRegex != null) {
				return false;
			}
		} else if (!applicationRegex.equals(other.applicationRegex)) {
			return false;
		}
		if (authenticatorId == null) {
			if (other.authenticatorId != null) {
				return false;
			}
		} else if (!authenticatorId.equals(other.authenticatorId)) {
			return false;
		}
		if (orgName == null) {
			if (other.orgName != null) {
				return false;
			}
		} else if (!orgName.equals(other.orgName)) {
			return false;
		}
		if (orgRegex == null) {
			if (other.orgRegex != null) {
				return false;
			}
		} else if (!orgRegex.equals(other.orgRegex)) {
			return false;
		}
		if (path == null) {
			if (other.path != null) {
				return false;
			}
		} else if (!path.equals(other.path)) {
			return false;
		}
		if (preferredRouteRegex == null) {
			if (other.preferredRouteRegex != null) {
				return false;
			}
		} else if (!preferredRouteRegex.equals(other.preferredRouteRegex)) {
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
		if (spaceRegex == null) {
			if (other.spaceRegex != null) {
				return false;
			}
		} else if (!spaceRegex.equals(other.spaceRegex)) {
			return false;
		}
		return true;
	}
	
	public static String[] labelNames() {
		final String[] labels = {
				"orgName", "orgRegex", "spaceName", "spaceRegex", "applicationName", "applicationRegex", "path", "protocol",
				"authenticatorId", "preferredRouteRegex"
		};
		
		return labels;
	}
	
	public String[] labelValues() {
		final String[] values = {
				protectAgainstNull(this.getOrgName()), protectAgainstNull(this.getOrgRegex()), 
				protectAgainstNull(this.getSpaceName()), protectAgainstNull(this.getSpaceRegex()), 
				protectAgainstNull(this.getApplicationName()), protectAgainstNull(this.getApplicationRegex()),
				protectAgainstNull(this.getPath()), protectAgainstNull(this.getProtocol()), protectAgainstNull(this.getAuthenticatorId()), 
				protectAgainstNull(Strings.join(this.getPreferredRouteRegex(), '|'))
		};
		
		return values;
	}
	
	public String protectAgainstNull(String original) {
		if (original == null) {
			return "";
		}
		
		return original;
	}
	
}
