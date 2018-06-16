package org.cloudfoundry.promregator.config;

import org.cloudfoundry.promregator.config.validations.ConfigurationValidation;
import org.junit.Assert;
import org.junit.Test;

public class ConfigurationValidationsTest {
	private class TestableConfigurationValidations extends ConfigurationValidations {
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
		ConfigurationValidation[] listOfValidations = {
				new AcceptingConfigurationValidation()
		};
		subject.listOfValidations = listOfValidations;
		
		subject.validateConfiguration();
		
		Assert.assertFalse(subject.called);
	}

	@Test
	public void testValidationFailed() {
		TestableConfigurationValidations subject = new TestableConfigurationValidations();
		ConfigurationValidation[] listOfValidations = {
				new FailingConfigurationValidation()
		};
		subject.listOfValidations = listOfValidations;
		
		subject.validateConfiguration();
		
		Assert.assertTrue(subject.called);
	}

}
