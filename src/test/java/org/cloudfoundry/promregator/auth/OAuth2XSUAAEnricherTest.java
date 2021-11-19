package org.cloudfoundry.promregator.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.OAuth2XSUAABasicAuthenticationConfiguration;
import org.cloudfoundry.promregator.mockServer.AuthenticationMockServer;
import org.cloudfoundry.promregator.mockServer.DefaultOAuthHttpHandler;
import org.hamcrest.Matchers;
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

class OAuth2XSUAAEnricherTest {
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
	void testAppropriateJWTCall() {
		this.ams.getOauthTokenHandler().setStatus(200);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer200Response);

		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig());

		HttpGet mockGet = Mockito.mock(HttpGet.class);
		Mockito.when(mockGet.getURI()).thenAnswer((Answer<URI>) invocation -> new URI("http://localhost/target"));

		subject.enrichWithAuthentication(mockGet);

		verify(mockGet).setHeader("Authorization", "Bearer someAccessToken");
	}

	@Test
	void testBadJWTCall() {
		this.ams.getOauthTokenHandler().setStatus(401);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer401Response);

		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig());

		HttpGet get = new HttpGet();
		subject.enrichWithAuthentication(get);

		// No header has been added (especially no Authorization header).
		// And we did not get an exception
		assertThat(get.getAllHeaders()).isEmpty();
	}

	@Test
	void testJWTCallIsBuffered() {
		this.ams.getOauthTokenHandler().setStatus(200);
		this.ams.getOauthTokenHandler().setResponse(this.oAuthServer200Response);

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

	@Test
	void testAuthorizationHeaderNotAddedAndNoExceptionThrownWhenTokenIsEmpty() throws Exception {
		ClientCredentialsTokenFlow tokenClientMock = Mockito.mock(ClientCredentialsTokenFlow.class);
		OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse("", 42l, "");
		Mockito.when(tokenClientMock.execute()).thenReturn(tokenResponse);

		OAuth2XSUAAEnricher subject = new OAuth2XSUAAEnricher(getConfig(), tokenClientMock);

		HttpGet get = new HttpGet();

		subject.enrichWithAuthentication(get);

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
