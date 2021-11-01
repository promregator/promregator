package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.lite.config.BasicAuthenticationConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class BasicAuthenticationEnricherTest {

	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	void testStraightForward() {
		BasicAuthenticationConfiguration config = new BasicAuthenticationConfiguration("dummyuser", "unittestpassword");

		BasicAuthenticationEnricher subject = new BasicAuthenticationEnricher(config);
		
		HttpGet mockGet = Mockito.mock(HttpGet.class);
		
		subject.enrichWithAuthentication(mockGet);
		
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
		Mockito.verify(mockGet).setHeader(keyCaptor.capture(), valueCaptor.capture());
		String value = valueCaptor.getValue();
		assertThat("Basic ZHVtbXl1c2VyOnVuaXR0ZXN0cGFzc3dvcmQ=").isEqualTo(value);
		
		String key = keyCaptor.getValue();
		assertThat("Authorization").isEqualTo(key);

	}

}

