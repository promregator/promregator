package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigurationTarget extends Target {
	public List<CommonConfigurationApplication> applications = new ArrayList<>();

	/**
	 * @return the applications
	 */
	public List<CommonConfigurationApplication> getApplications() {
		return applications;
	}

	/**
	 * @param applications the applications to set
	 */
	public void setApplications(List<CommonConfigurationApplication> applications) {
		this.applications = new ArrayList<>(applications);
	}
	
	public List<Target> unrollTargets() {
		if (this.applications == null | this.applications.size() == 0) {
			return Collections.singletonList(this);
		}
		
		List<Target> result = new ArrayList<>(this.applications.size());
		
		for (CommonConfigurationApplication cca : this.applications) {
			Target newTarget = new Target(this);
			newTarget.copyFromCommonConfigurationApplication(cca);
			
			result.add(newTarget);
		}
		
		return result;
	}
}
