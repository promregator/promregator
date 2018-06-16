package org.cloudfoundry.promregator.config.validations;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;

/**
 * This interface defines the structure of validations which can be called by
 * the central configuration validation framework.
 */
public interface ConfigurationValidation {
	/**
	 * executes the validation.
	 * 
	 * @param promregatorConfiguration
	 *            the global configuration, which shall be validated
	 * @return an error message indicating the nature of the failure during
	 *         validation, or <code>null</code> if no error has happened during
	 *         validation.
	 */
	String validate(PromregatorConfiguration promregatorConfiguration);
}
