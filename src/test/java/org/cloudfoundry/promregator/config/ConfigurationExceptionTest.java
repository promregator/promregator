package org.cloudfoundry.promregator.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigurationExceptionTest {

	@Test
	public void testConfigurationExceptionStringThrowable() {
		Assertions.assertNotNull(new ConfigurationException("Test", new Exception()));
	}

	@Test
	public void testConfigurationExceptionString() {
		Assertions.assertNotNull(new ConfigurationException("Test"));
	}

}
