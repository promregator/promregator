package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class BasicAuthenticationEnricherTest {

	@Test
	public void testStraightForward() {
		BasicAuthenticationConfiguration config = new BasicAuthenticationConfiguration();
		config.setUsername("dummyuser");
		config.setPassword("unittestpassword");
		
		BasicAuthenticationEnricher subject = new BasicAuthenticationEnricher(config);
		
		HttpGet mockGet = Mockito.mock(HttpGet.class);
		
		subject.enrichWithAuthentication(mockGet);
		
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
		Mockito.verify(mockGet).setHeader(keyCaptor.capture(), valueCaptor.capture());
		String value = valueCaptor.getValue();
		Assert.assertEquals("Basic ZHVtbXl1c2VyOnVuaXR0ZXN0cGFzc3dvcmQ=", value);
		
		String key = keyCaptor.getValue();
		Assert.assertEquals("Authorization", key);

	}

}

