package org.cloudfoundry.promregator.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class BasicAuthenticationEnricherTest {

	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	void testStraightForward() {
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
		assertThat(value).isEqualTo("Basic ZHVtbXl1c2VyOnVuaXR0ZXN0cGFzc3dvcmQ=");
		
		String key = keyCaptor.getValue();
		assertThat(key).isEqualTo("Authorization");

	}

}

