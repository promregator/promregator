package org.cloudfoundry.promregator.config;

import java.io.Serial;

public class ConfigurationException extends Exception {

	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 5068571824175119968L;

	public ConfigurationException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public ConfigurationException(String arg0) {
		super(arg0);
	}

	
}
