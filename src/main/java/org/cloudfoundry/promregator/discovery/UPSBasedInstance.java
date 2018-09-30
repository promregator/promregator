package org.cloudfoundry.promregator.discovery;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;

public class UPSBasedInstance extends Instance {
	private String orgName;
	private String spaceName;
	private String applicationName;
	private AuthenticationEnricher ae;

	public UPSBasedInstance(String instanceId, String accessUrl, String orgName, String spaceName,
			String applicationName, AuthenticationEnricher ae) {
		super(instanceId, accessUrl);
		this.orgName = orgName;
		this.spaceName = spaceName;
		this.applicationName = applicationName;
		this.ae = ae;
	}

	@Override
	public String getOrgName() {
		return this.orgName;
	}

	@Override
	public String getSpaceName() {
		return this.spaceName;
	}

	@Override
	public String getApplicationName() {
		return this.applicationName;
	}

	@Override
	public AuthenticationEnricher getAuthenticationEnricher() {
		return this.ae;
	}

}
