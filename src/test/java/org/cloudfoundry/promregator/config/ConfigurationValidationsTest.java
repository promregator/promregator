package org.cloudfoundry.promregator.config;

import org.cloudfoundry.promregator.config.validations.ConfigurationValidation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class ConfigurationValidationsTest {
	private static class TestableConfigurationValidations extends ConfigurationValidations {
		public boolean called = false;
		/* (non-Javadoc)
		 * @see org.cloudfoundry.promregator.config.ConfigurationValidations#shutdownApplicationContext(java.lang.String)
		 */
		@Override
		protected void shutdownApplicationContext(String errorMessage) {
			called = true;
		}
		
	}
	
	private static class FailingConfigurationValidation implements ConfigurationValidation {

		@Override
		public String validate(PromregatorConfiguration promregatorConfiguration) {
			return "failed";
		}
		
	}
	
	private static class AcceptingConfigurationValidation implements ConfigurationValidation {

		@Override
		public String validate(PromregatorConfiguration promregatorConfiguration) {
			return null;
		}
		
	}
	
	@Test
	public void testNoValidationFailed() {
		TestableConfigurationValidations subject = new TestableConfigurationValidations();
		List<ConfigurationValidation> listOfValidations = Collections.singletonList(new AcceptingConfigurationValidation());
		subject.listOfValidations = listOfValidations;
		
		subject.validateConfiguration();
		
		Assertions.assertFalse(subject.called);
	}

	@Test
	public void testValidationFailed() {
		TestableConfigurationValidations subject = new TestableConfigurationValidations();
		List<ConfigurationValidation> listOfValidations = Collections.singletonList(new FailingConfigurationValidation());
		subject.listOfValidations = listOfValidations;
		
		subject.validateConfiguration();
		
		Assertions.assertTrue(subject.called);
	}

}
