package org.cloudfoundry.promregator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.checkerframework.checker.nullness.qual.NonNull;

public class Target {
	private static final Logger log = LoggerFactory.getLogger(Target.class);

	private String orgName;

	private String orgRegex;

	private String spaceName;

	private String spaceRegex;

	private String applicationName;

	private String applicationRegex;

	private String path;

	private Boolean kubernetesAnnotations = false;

	private String protocol;

	private String authenticatorId;

	private List<String> preferredRouteRegex;

	private List<Pattern> cachedPreferredRouteRegexPattern;

	private int internalRoutePort;

	public Target() {
		super();
	}

	public int getInternalRoutePort() {
		return internalRoutePort;
	}

	public void setInternalRoutePort(int internalRoutePort) {
		this.internalRoutePort = internalRoutePort;
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
		if (source.kubernetesAnnotations != null)
			this.kubernetesAnnotations = source.kubernetesAnnotations;

		this.protocol = source.protocol;
		this.authenticatorId = source.authenticatorId;
		this.internalRoutePort = source.internalRoutePort;
		
		if (source.preferredRouteRegex == null) {
			this.preferredRouteRegex = new ArrayList<>(0);
		} else {
			this.preferredRouteRegex = new ArrayList<>(source.preferredRouteRegex);
		}
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

	public String getAuthenticatorId() {
		return authenticatorId;
	}

	public void setAuthenticatorId(String authenticatorId) {
		this.authenticatorId = authenticatorId;
	}

	/**
	 * @return the preferredRouteRegex
	 * This will never return a null value
	 */
	public @NonNull List<String> getPreferredRouteRegex() {
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

	/**
	 * @return the list of preferred Route Regex Patterns
	  * This will never return a null value
	 */
	public @NonNull List<Pattern> getPreferredRouteRegexPatterns() {
		if (this.cachedPreferredRouteRegexPattern != null) {
			return this.cachedPreferredRouteRegexPattern;
		}
		
		List<String> regexStringList = this.getPreferredRouteRegex();
		
		List<Pattern> patterns = new ArrayList<>(regexStringList.size());
		for (String routeRegex : regexStringList) {
			try {
				Pattern pattern = Pattern.compile(routeRegex);
				patterns.add(pattern);
			} catch (PatternSyntaxException e) {
				log.warn(String.format("Invalid preferredRouteRegex '%s' detected. Fix your configuration; until then, the regex will be ignored", routeRegex), e);
				// continue not necessary here
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
		builder.append(", kubernetesAnnotations=");
		builder.append(kubernetesAnnotations.toString());
		builder.append(", protocol=");
		builder.append(protocol);
		builder.append(", authenticatorId=");
		builder.append(authenticatorId);
		builder.append(", preferredRouteRegex=");
		builder.append(preferredRouteRegex);
		builder.append(", internalRoutePort=");
		builder.append(internalRoutePort);
		builder.append("]");
		return builder.toString();
	}

}
