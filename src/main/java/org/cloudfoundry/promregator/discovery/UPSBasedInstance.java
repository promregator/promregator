package org.cloudfoundry.promregator.discovery;

import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.NullEnricher;

public class UPSBasedInstance extends Instance {
	private String orgName;
	private String spaceName;
	private String applicationName;
	private String path;

	public UPSBasedInstance(String instanceId, String accessUrl, String orgName, String spaceName,
			String applicationName, String path) {
		super(instanceId, accessUrl);
		this.orgName = orgName;
		this.spaceName = spaceName;
		this.applicationName = applicationName;
		this.path = path;
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
	public String getPath() {
		return this.path;
	}

	@Override
	public AuthenticationEnricher getAuthenticationEnricher() {
		// TODO Auto-generated method stub
		return new NullEnricher();
	}

}
