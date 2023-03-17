package org.cloudfoundry.promregator.cfaccessor.client;

import org.cloudfoundry.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InfoV3 {

	private @Nullable String build;

	/**
	 * @return the build
	 */
	@JsonProperty("build")
	public String getBuild() {
		return build;
	}

	/**
	 * @param build the build to set
	 */
	@JsonProperty("build")
	public void setBuild(String build) {
		this.build = build;
	}
	
}
