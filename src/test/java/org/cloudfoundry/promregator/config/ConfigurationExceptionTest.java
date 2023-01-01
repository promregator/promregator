package org.cloudfoundry.promregator.config;

import org.junit.jupiter.api.Test;

public class ConfigurationExceptionTest {

	@Test
	public void testConfigurationExceptionStringThrowable() {
		new ConfigurationException("Test", new Exception());
	}

	@Test
	public void testConfigurationExceptionString() {
		new ConfigurationException("Test");
	}

}
