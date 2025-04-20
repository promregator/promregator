package org.cloudfoundry.promregator.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.OAuth2XSUAABasicAuthenticationConfiguration;
import org.cloudfoundry.promregator.mockServer.AuthenticationMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.sap.cloud.security.xsuaa.client.OAuth2TokenResponse;
import com.sap.cloud.security.xsuaa.tokenflows.ClientCredentialsTokenFlow;
import com.sap.cloud.security.xsuaa.tokenflows.TokenFlowException;

public class OAuth2XSUAAEnricherTest {
	/*
	 * TODO: This implementation of unit tests may benefit from using 
	 * https://github.com/SAP/cloud-security-xsuaa-integration/tree/1c133ee17b629b07d58a7aec512daf98b067c031/java-security-test 
	 */
	private String oAuthServer200Response = """
			{
			    "access_token": "someAccessToken",
			    "token_type": "bearer",
			    "expires_in": 43199,
			    "scope": "dummyScope.AdminOnboarding uaa.resource",
			    "jti": "01234567890"
			}\
			""";

	private String oAuthServer401Response = "{\"error\":\"unauthorized\",\"error_description\":\"Bad credentials\"}";
	private AuthenticationMockServer ams;
	
	@BeforeEach
	public void startUpAuthenticationServer() throws IOException {
		this.ams = new AuthenticationMockServer();
		this.ams.start();
	}
	
	@AfterEach
	public void tearDownAuthenticationServer() {
		this.ams.stop();
	}
	
	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Test
	void testAppropriateJWTCall() throws IOException {
		this.ams.getOauthTokenHandler().setStatus(200);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer200Response);

		HttpGet mockGet = Mockito.mock(HttpGet.class);
		Mockito.when(mockGet.getURI()).thenAnswer((Answer<URI>) invocation -> new URI("http://localhost/target"));

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig())) {
			subject.enrichWithAuthentication(mockGet);
		}

		verify(mockGet).setHeader("Authorization", "Bearer someAccessToken");
	}

	@Test
	void testBadJWTCall() throws IOException {
		this.ams.getOauthTokenHandler().setStatus(401);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer401Response);

		HttpGet get = new HttpGet();

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig())) {
			subject.enrichWithAuthentication(get);
		}

		// No header has been added (especially no Authorization header).
		// And we did not get an exception
		assertThat(get.getAllHeaders()).isEmpty();
	}

	@Test
	void testJWTCallIsBuffered() throws IOException {
		this.ams.getOauthTokenHandler().setStatus(200);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer200Response);

		HttpGet mockGet = Mockito.mock(HttpGet.class);
		Mockito.when(mockGet.getURI()).thenAnswer(new Answer<URI>() {

			@Override
			public URI answer(InvocationOnMock invocation) throws Throwable {
				return new URI("http://localhost/target");
			}

		});

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig())) {
			// first call will trigger OAuth request
			subject.enrichWithAuthentication(mockGet);
	
			// second one should not
			subject.enrichWithAuthentication(mockGet);
		}

		assertThat(this.ams.getOauthTokenHandler().getCounterCalled()).isEqualTo(1);
	}

	@Test
	void testAuthorizationHeaderAddedWhenTokenRetrieved() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		Mockito.when(tokenClientMock.execute()).thenReturn(new OAuth2TokenResponse("someAccessToken", 42l, "someRefreshToken"));

		ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);

		HttpGet get = new HttpGet();
		assertThat(get.getAllHeaders()).isEmpty();
		
		final OAuth2XSUAABasicAuthenticationConfiguration config = getConfig("aScope anotherScope");
		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(config, tokenClientMock)) {
			subject.enrichWithAuthentication(get);
		}

		Mockito.verify(tokenClientMock).scopes(captor.capture());
		final List<String[]> capturedValues = captor.getAllValues();
		assertThat(capturedValues.size()).isEqualTo(1);
		String[] capturedValue = capturedValues.get(0);
		
		assertThat(capturedValue.length).isEqualTo(2);
		
		assertThat(capturedValue).contains("aScope", "anotherScope");

		assertThat(get.getHeaders("Authorization")[0].getValue()).isEqualTo("Bearer someAccessToken");
	}

	@Test
	void testAuthorizationHeaderNotAddedAndNoExceptionThrownWhenTokenNotRetrieved() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		Mockito.when(tokenClientMock.execute()).thenThrow(new TokenFlowException("ups, something went wrong"));

		HttpGet get = new HttpGet();

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig(), tokenClientMock)) {
			subject.enrichWithAuthentication(get);
		}

		assertThat(get.getAllHeaders()).isEmpty();
	}

	@Test
	void testAuthorizationHeaderNotAddedAndNoExceptionThrownWhenTokenIsEmpty() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse("", 42l, "");
		Mockito.when(tokenClientMock.execute()).thenReturn(tokenResponse);

		HttpGet get = new HttpGet();

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig(), tokenClientMock)) {
			subject.enrichWithAuthentication(get);
		}

		assertThat(get.getAllHeaders()).isEmpty();
	}

	private OAuth2XSUAABasicAuthenticationConfiguration getConfig() {
		return getConfig(null);
	}
	private OAuth2XSUAABasicAuthenticationConfiguration getConfig(String scopes) {
		OAuth2XSUAABasicAuthenticationConfiguration authenticatorConfig = new OAuth2XSUAABasicAuthenticationConfiguration();
		authenticatorConfig.setClient_id("client_id");
		authenticatorConfig.setClient_secret("client_secret");
		authenticatorConfig.setTokenServiceURL("http://localhost:9001/oauth/token");
		if (! StringUtils.isEmpty(scopes)) {
			authenticatorConfig.setScopes(scopes);
		}
		return authenticatorConfig;
	}

}
