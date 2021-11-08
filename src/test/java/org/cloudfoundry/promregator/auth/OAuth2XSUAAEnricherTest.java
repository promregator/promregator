package org.cloudfoundry.promregator.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;

import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration;
import org.cloudfoundry.promregator.mockServer.AuthenticationMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.base.Strings;
import com.sap.cloud.security.xsuaa.client.OAuth2TokenResponse;
import com.sap.cloud.security.xsuaa.tokenflows.ClientCredentialsTokenFlow;
import com.sap.cloud.security.xsuaa.tokenflows.TokenFlowException;

class OAuth2XSUAAEnricherTest {
	private String oAuthServerResponse = "{\n" + 
			"    \"access_token\": \"someAccessToken\",\n" + 
			"    \"token_type\": \"bearer\",\n" + 
			"    \"expires_in\": 43199,\n" + 
			"    \"scope\": \"dummyScope.AdminOnboarding uaa.resource\",\n" + 
			"    \"jti\": \"01234567890\"\n" + 
			"}";
	
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
	void testAppropriateJWTCall() {
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServerResponse);

		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig());

		HttpGet mockGet = Mockito.mock(HttpGet.class);
		Mockito.when(mockGet.getURI()).thenAnswer((Answer<URI>) invocation -> new URI("http://localhost/target"));

		subject.enrichWithAuthentication(mockGet);

		verify(mockGet).setHeader("Authorization", "Bearer someAccessToken");
	}

	@Test
	void testJWTCallIsBuffered() {
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServerResponse);

		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig());

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

		assertThat(this.ams.getOauthTokenHandler().getCounterCalled()).isEqualTo(1);
	}

	@Test
	void testAuthorizationHeaderAddedWhenTokenRetrieved() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		Mockito.when(tokenClientMock.execute())
				.thenReturn(new OAuth2TokenResponse("someAccessToken", 42l, "someRefreshToken"));

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig("aScope anotherScope"), tokenClientMock);

		HttpGet get = new HttpGet();

		assertThat(get.getAllHeaders()).isEmpty();

		subject.enrichWithAuthentication(get);

		Mockito.verify(tokenClientMock).scopes(captor.capture());
		assertThat(captor.getAllValues()).contains("aScope", "anotherScope");
		assertThat(captor.getAllValues()).hasSize(2);

		assertThat(get.getHeaders("Authorization")[0].getValue()).isEqualTo("Bearer someAccessToken");
	}

	@Test
	void testAuthorizationHeaderNotAddedAndNoExceptionThrownWhenTokenNotRetrieved() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		Mockito.when(tokenClientMock.execute()).thenThrow(new TokenFlowException("ups, something went wrong"));

		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig(), tokenClientMock);

		HttpGet get = new HttpGet();

		subject.enrichWithAuthentication(get);

		assertThat(get.getAllHeaders()).isEmpty();
	}

	private OAuth2XSUAAAuthenticationConfiguration getConfig() {
		return getConfig(null);
	}
	private OAuth2XSUAAAuthenticationConfiguration getConfig(String scopes) {
		OAuth2XSUAAAuthenticationConfiguration authenticatorConfig = new OAuth2XSUAAAuthenticationConfiguration();
		authenticatorConfig.setClient_id("client_id");
		authenticatorConfig.setClient_secret("client_secret");
		authenticatorConfig.setTokenServiceURL("http://localhost:9001/oauth/token");
		if (! Strings.isNullOrEmpty(scopes)) {
			authenticatorConfig.setScopes(scopes);
		}
		return authenticatorConfig;
	}

}
