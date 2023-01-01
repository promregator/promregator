package org.cloudfoundry.promregator.config;

import org.junit.jupiter.api.Test;

class ConfigurationExceptionTest {

	@Test
	void testConfigurationExceptionStringThrowable() {
		new ConfigurationException("Test", new Exception());
	}

	@Test
	void testConfigurationExceptionString() {
		new ConfigurationException("Test");
	}

}
