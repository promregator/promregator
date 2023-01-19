package org.cloudfoundry.promregator.config;

import javax.annotation.PostConstruct;

import org.cloudfoundry.promregator.config.validations.ConfigurationValidation;
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ConfigurationValidations {
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;
	@Autowired
	protected List<ConfigurationValidation> listOfValidations;
	
	@PostConstruct
	protected void validateConfiguration() {
		for (ConfigurationValidation validation : listOfValidations) {
			String result = validation.validate(promregatorConfiguration);
			if (result != null) {
				this.shutdownApplicationContext(result);
			}
		}
	}

	private static class ConfigurationError extends Error {

		private static final long serialVersionUID = -4675633683803690983L;

		public ConfigurationError(String arg0) {
			super(arg0);
		}

	}

	/**
	 * shuts the application down; to be used, if a configuration error is detected.
	 * 
	 * @param errorMessage
	 *            the error message with which the application shall be shut down.
	 */
	protected void shutdownApplicationContext(String errorMessage) {
		throw new ConfigurationError(errorMessage);
	}


}
