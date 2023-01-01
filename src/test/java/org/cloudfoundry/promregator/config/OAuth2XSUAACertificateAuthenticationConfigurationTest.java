package org.cloudfoundry.promregator.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OAuth2XSUAACertificateAuthenticationConfigurationTest {

	@Test
	public void testStraightForward() {
		OAuth2XSUAACertificateAuthenticationConfiguration subject = new OAuth2XSUAACertificateAuthenticationConfiguration();
		
		subject.setClient_certificates("xyz");
		subject.setClient_id("clientid");
		subject.setClient_key("key");
		subject.setTokenServiceCertURL("url");
		
		Assertions.assertEquals("xyz", subject.getClient_certificates());
		Assertions.assertEquals("clientid", subject.getClient_id());
		Assertions.assertEquals("key", subject.getClient_key());
		Assertions.assertEquals("url", subject.getTokenServiceCertURL());
	}

}
