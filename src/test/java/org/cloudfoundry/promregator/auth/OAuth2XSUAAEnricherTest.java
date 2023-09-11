package org.cloudfoundry.promregator.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.OAuth2XSUAABasicAuthenticationConfiguration;
import org.cloudfoundry.promregator.mockServer.AuthenticationMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.sap.cloud.security.xsuaa.client.OAuth2TokenResponse;
import com.sap.cloud.security.xsuaa.tokenflows.ClientCredentialsTokenFlow;
import com.sap.cloud.security.xsuaa.tokenflows.TokenFlowException;

public class OAuth2XSUAAEnricherTest {
	/*
	 * TODO: This implementation of unit tests may benefit from using 
	 * https://github.com/SAP/cloud-security-xsuaa-integration/tree/1c133ee17b629b07d58a7aec512daf98b067c031/java-security-test 
	 */
	private String oAuthServer200Response = "{\n" +
			"    \"access_token\": \"someAccessToken\",\n" + 
			"    \"token_type\": \"bearer\",\n" + 
			"    \"expires_in\": 43199,\n" + 
			"    \"scope\": \"dummyScope.AdminOnboarding uaa.resource\",\n" + 
			"    \"jti\": \"01234567890\"\n" + 
			"}";

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

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig())) {
			subject.enrichWithAuthentication(mockGet);
		}

		verify(mockGet).setHeader("Authorization", "Bearer someAccessToken");
	}

	@Test
	void testBadJWTCall() throws IOException {
		this.ams.getOauthTokenHandler().setStatus(401);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer401Response);

		HttpGet get = Mockito.mock(HttpGet.class);

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig())) {
			subject.enrichWithAuthentication(get);
		}

		// No header has been added (especially no Authorization header).
		// And we did not get an exception
		verify(get, never()).setHeader(anyString(), any());
	}

	@Test
	void testJWTCallIsBuffered() throws IOException {
		this.ams.getOauthTokenHandler().setStatus(200);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer200Response);

		HttpGet mockGet = Mockito.mock(HttpGet.class);

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
		Mockito.when(tokenClientMock.execute())
				.thenReturn(new OAuth2TokenResponse("someAccessToken", 42l, "someRefreshToken"));

		ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);

		HttpGet get = Mockito.mock(HttpGet.class);
		
		OAuth2XSUAABasicAuthenticationConfiguration config = getConfig("aScope anotherScope");
		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(config, tokenClientMock)) {
			subject.enrichWithAuthentication(get);
		}

		Mockito.verify(tokenClientMock).scopes(captor.capture());
		List<String[]> allValues = captor.getAllValues();
		assertThat(allValues).hasSize(1);
		String[] singleCall = allValues.get(0);
		assertThat(singleCall).contains("aScope", "anotherScope");
		assertThat(singleCall).hasSize(2);

		Mockito.verify(get).setHeader("Authorization", "Bearer someAccessToken");
	}

	@Test
	void testAuthorizationHeaderNotAddedAndNoExceptionThrownWhenTokenNotRetrieved() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		Mockito.when(tokenClientMock.execute()).thenThrow(new TokenFlowException("ups, something went wrong"));

		HttpGet get = Mockito.mock(HttpGet.class);

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig(), tokenClientMock)) {
			subject.enrichWithAuthentication(get);
		}

		verify(get, never()).setHeader(anyString(), any());
	}

	@Test
	void testAuthorizationHeaderNotAddedAndNoExceptionThrownWhenTokenIsEmpty() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse("", 42l, "");
		Mockito.when(tokenClientMock.execute()).thenReturn(tokenResponse);

		HttpGet get = Mockito.mock(HttpGet.class);

		try (OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig(), tokenClientMock)) {
			subject.enrichWithAuthentication(get);
		}

		verify(get, never()).setHeader(anyString(), any());
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
