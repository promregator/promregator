package org.cloudfoundry.promregator.auth;

import java.io.IOException;
import java.net.URI;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;
import org.cloudfoundry.promregator.mockServer.AuthenticationMockServer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class OAuth2XSUAAEnricherTest {
	private String oAuthServerResponse = "{\n" + 
			"    \"access_token\": \"someAccessToken\",\n" + 
			"    \"token_type\": \"bearer\",\n" + 
			"    \"expires_in\": 43199,\n" + 
			"    \"scope\": \"dummyScope.AdminOnboarding uaa.resource\",\n" + 
			"    \"jti\": \"01234567890\"\n" + 
			"}";
	
	private AuthenticationMockServer ams;
	
	@Before
	public void startUpAuthenticationServer() throws IOException {
		this.ams = new AuthenticationMockServer();
		this.ams.start();
	}
	
	@After
	public void tearDownAuthenticationServer() {
		this.ams.stop();
	}
	
	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	public void testAppropriateJWTCall() {
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServerResponse);
		
		OAuth2XSUAAAuthenticationConfiguration authenticatorConfig = new OAuth2XSUAAAuthenticationConfiguration();
		authenticatorConfig.setClient_id("client_id");
		authenticatorConfig.setClient_secret("client_secret");
		authenticatorConfig.setTokenServiceURL("http://localhost:9001/oauth/token");
		
		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(authenticatorConfig);
		
		HttpGet mockGet = Mockito.mock(HttpGet.class);
		Mockito.when(mockGet.getURI()).thenAnswer(new Answer<URI>() {

			@Override
			public URI answer(InvocationOnMock invocation) throws Throwable {
				return new URI("http://localhost/target");
			}
			
		});
		
		subject.enrichWithAuthentication(mockGet);
		
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
		Mockito.verify(mockGet).setHeader(keyCaptor.capture(), valueCaptor.capture());
		String value = valueCaptor.getValue();
		Assert.assertEquals("Bearer someAccessToken", value);
		
		String key = keyCaptor.getValue();
		Assert.assertEquals("Authorization", key);
	}

	@Test
	public void testJWTCallIsBuffered() {
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServerResponse);
		
		OAuth2XSUAAAuthenticationConfiguration authenticatorConfig = new OAuth2XSUAAAuthenticationConfiguration();
		authenticatorConfig.setClient_id("client_id");
		authenticatorConfig.setClient_secret("client_secret");
		authenticatorConfig.setTokenServiceURL("http://localhost:9001/oauth/token");
		
		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(authenticatorConfig);
		
		HttpGet mockGet = Mockito.mock(HttpGet.class);
		Mockito.when(mockGet.getURI()).thenAnswer(new Answer<URI>() {

			@Override
			public URI answer(InvocationOnMock invocation) throws Throwable {
				return new URI("http://localhost/target");
			}
			
		});
		
		// first call will trigger OAuth request
		subject.enrichWithAuthentication(mockGet);
		
		// second one should not
		subject.enrichWithAuthentication(mockGet);
		
		Assert.assertEquals(1, this.ams.getOauthTokenHandler().getCounterCalled());
	}
	
}
