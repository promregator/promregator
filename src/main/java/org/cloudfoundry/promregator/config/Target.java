package org.cloudfoundry.promregator.config;

import org.apache.log4j.Logger;

public class Target extends CommonConfigurationApplication implements Cloneable {
	private static final Logger log = Logger.getLogger(Target.class);
	
	private String orgName;
	
	private String orgRegex;
	
	private String spaceName;
	
	private String spaceRegex;
	
	public Target() {
		super();
	}
	
	/**
	 * creates a copy of an existing Target
	 * @param source the template which shall be used for copying
	 */
	public Target(Target source) {
		super(source.getApplicationName(), source.getApplicationRegex(), source.getPath(), source.getProtocol(), source.getAuthenticatorId(), source.getPreferredRouteRegex());
		
		this.orgName = source.orgName;
		this.orgRegex = source.orgRegex;
		this.spaceName = source.spaceName;
		this.spaceRegex = source.spaceRegex;
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
		builder.append(", getApplicationName()=");
		builder.append(getApplicationName());
		builder.append(", getApplicationRegex()=");
		builder.append(getApplicationRegex());
		builder.append(", getPath()=");
		builder.append(getPath());
		builder.append(", getProtocol()=");
		builder.append(getProtocol());
		builder.append(", getAuthenticatorId()=");
		builder.append(getAuthenticatorId());
		builder.append(", getPreferredRouteRegex()=");
		builder.append(getPreferredRouteRegex());
		builder.append("]");
		return builder.toString();
	}

}
