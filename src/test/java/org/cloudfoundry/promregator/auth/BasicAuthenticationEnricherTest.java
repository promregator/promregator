package org.cloudfoundry.promregator.auth;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

public class BasicAuthenticationEnricherTest {

	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	public void testStraightForward() {
		BasicAuthenticationConfiguration config = new BasicAuthenticationConfiguration();
		config.setUsername("dummyuser");
		config.setPassword("unittestpassword");
		
		BasicAuthenticationEnricher subject = new BasicAuthenticationEnricher(config);
		
		CheckingHTTPRequestFacade facade = new CheckingHTTPRequestFacade();
		
		subject.enrichWithAuthentication(facade);
		
		Assert.assertTrue(facade.isChecked());
	}

	private class CheckingHTTPRequestFacade implements HTTPRequestFacade {
		private boolean checked = false;
		
		@Override
		public void addHeader(String name, String value) {
			Assert.assertEquals("Authorization", name);
			Assert.assertEquals("Basic ZHVtbXl1c2VyOnVuaXR0ZXN0cGFzc3dvcmQ=", value);
			checked = true;
		}

		public boolean isChecked() {
			return checked;
		}
		
	}
	
}

