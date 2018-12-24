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
		
		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(authenticatorConfig, null, 0);
		
		CheckingHTTPRequestFacade facade = new CheckingHTTPRequestFacade();
		
		subject.enrichWithAuthentication(facade);
		
		Assert.assertTrue(facade.isChecked());

	}

	@Test
	public void testJWTCallIsBuffered() {
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServerResponse);
		
		OAuth2XSUAAAuthenticationConfiguration authenticatorConfig = new OAuth2XSUAAAuthenticationConfiguration();
		authenticatorConfig.setClient_id("client_id");
		authenticatorConfig.setClient_secret("client_secret");
		authenticatorConfig.setTokenServiceURL("http://localhost:9001/oauth/token");
		
		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(authenticatorConfig, null, 0);
		
		HttpGet mockGet = Mockito.mock(HttpGet.class);
		Mockito.when(mockGet.getURI()).thenAnswer(new Answer<URI>() {

			@Override
			public URI answer(InvocationOnMock invocation) throws Throwable {
				return new URI("http://localhost/target");
			}
			
		});
		
		CheckingHTTPRequestFacade facade = new CheckingHTTPRequestFacade();
		
		// first call will trigger OAuth request
		subject.enrichWithAuthentication(facade);
		
		Assert.assertTrue(facade.isChecked());

		facade = null;
		facade = new CheckingHTTPRequestFacade();
		
		// second one should not
		subject.enrichWithAuthentication(facade);
		
		Assert.assertTrue(facade.isChecked());
		
		Assert.assertEquals(1, this.ams.getOauthTokenHandler().getCounterCalled());
	}
	
	private class CheckingHTTPRequestFacade implements HTTPRequestFacade {
		private boolean checked = false;
		
		@Override
		public void addHeader(String name, String value) {
			Assert.assertEquals("Authorization", name);
			Assert.assertEquals("Bearer someAccessToken", value);
			checked = true;
		}

		public boolean isChecked() {
			return checked;
		}
		
	}

}
