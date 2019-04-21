package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;

/**
 * All configuration options you may make on the level of an application
 *
 */
public class CommonConfigurationApplication {
	private static final Logger log = Logger.getLogger(CommonConfigurationApplication.class);
	
	private String applicationName;
	private String applicationRegex;
	private String path;
	private String protocol;
	private String authenticatorId;
	private List<String> preferredRouteRegex;
	private List<Pattern> cachedPreferredRouteRegexPattern;

	public CommonConfigurationApplication() {
		super();
	}
	
	public CommonConfigurationApplication(String applicationName, String applicationRegex, String path, String protocol,
			String authenticatorId, List<String> preferredRouteRegex) {
		super();
		this.applicationName = applicationName;
		this.applicationRegex = applicationRegex;
		this.path = path;
		this.protocol = protocol;
		this.authenticatorId = authenticatorId;
		
		if (preferredRouteRegex == null) {
			this.preferredRouteRegex = null;
		} else {
			this.preferredRouteRegex = new ArrayList<>(preferredRouteRegex);
		}
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

	public String getPathOrDefault() {
		if (this.path == null)
			return "/metrics";
		
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getProtocolOrDefault() {
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
			return Collections.emptyList();
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
	
	public void copyFromCommonConfigurationApplication(CommonConfigurationApplication other) {
		
		if (other.applicationName != null) {
			this.setApplicationName(other.applicationName);
		}
		
		if (other.applicationRegex != null) {
			this.setApplicationRegex(other.applicationRegex);
		}
		
		if (other.authenticatorId != null) {
			this.setAuthenticatorId(other.authenticatorId);
		}
		
		if (other.path != null) {
			this.setPath(other.path);
		}
		
		if (other.preferredRouteRegex != null) {
			this.setPreferredRouteRegex(other.preferredRouteRegex);
		}
		
		if (other.protocol != null) {
			this.setProtocol(other.protocol);
		}
	}

}
