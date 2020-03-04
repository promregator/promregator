package org.cloudfoundry.promregator.auth

import org.apache.http.client.methods.HttpGet
import org.assertj.core.api.Assertions
import org.cloudfoundry.promregator.JUnitTestUtils
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration
import org.cloudfoundry.promregator.mockServer.AuthenticationMockServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.io.IOException
import java.net.URI

class OAuth2XSUAAEnricherTest {
    private val oAuthServerResponse = "{\n" +
            "    \"access_token\": \"someAccessToken\",\n" +
            "    \"token_type\": \"bearer\",\n" +
            "    \"expires_in\": 43199,\n" +
            "    \"scope\": \"dummyScope.AdminOnboarding uaa.resource\",\n" +
            "    \"jti\": \"01234567890\"\n" +
            "}"
    private var ams: AuthenticationMockServer? = null
    @BeforeEach
    fun startUpAuthenticationServer() {
        ams = AuthenticationMockServer()
        ams!!.start()
    }

    @AfterEach
    fun tearDownAuthenticationServer() {
        ams!!.stop()
    }

    @Test
    fun testAppropriateJWTCall() {
        ams!!.oauthTokenHandler.response = oAuthServerResponse
        val authenticatorConfig = OAuth2XSUAAAuthenticationConfiguration(
                client_id = "client_id",
                client_secret = "client_secret",
                tokenServiceURL = "http://localhost:9001/oauth/token"
        )

        val subject = OAuth2XSUAAEnricher(authenticatorConfig)
        val mockGet = Mockito.mock(HttpGet::class.java)
        Mockito.`when`(mockGet.uri).thenAnswer(Answer { invocation: InvocationOnMock? -> URI("http://localhost/target") } as Answer<URI>)
        subject.enrichWithAuthentication(mockGet)
        Mockito.verify(mockGet).setHeader("Authorization", "Bearer someAccessToken")
    }

    @Test
    fun testJWTCallIsBuffered() {
        ams!!.oauthTokenHandler.response = oAuthServerResponse
        val authenticatorConfig = OAuth2XSUAAAuthenticationConfiguration(
                client_id = "client_id",
                client_secret = "client_secret",
                tokenServiceURL = "http://localhost:9001/oauth/token"
        )

        val subject = OAuth2XSUAAEnricher(authenticatorConfig)
        val mockGet = Mockito.mock(HttpGet::class.java)
        Mockito.`when`(mockGet.uri).thenAnswer { URI("http://localhost/target") }
        // first call will trigger OAuth request
        subject.enrichWithAuthentication(mockGet)
        // second one should not
        subject.enrichWithAuthentication(mockGet)
        Assertions.assertThat(ams!!.oauthTokenHandler.counterCalled).isEqualTo(1)
    }

    companion object {
        @AfterAll
        fun cleanupEnvironment() {
            JUnitTestUtils.cleanUpAll()
        }
    }
}