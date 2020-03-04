package org.cloudfoundry.promregator.auth

import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.config.AuthenticatorConfiguration
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration
import org.cloudfoundry.promregator.config.OAuth2XSUAAAuthenticationConfiguration
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.Target
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthenticatorControllerTest {
    private lateinit var subject: AuthenticatorController
    private lateinit var promregatorConfiguration: PromregatorConfiguration

    @BeforeEach
    fun setup() {
        promregatorConfiguration = PromregatorConfiguration(
                authenticator = AuthenticatorConfiguration(type = "none"),
                targetAuthenticators = listOf(
                        AuthenticatorConfiguration("unittestAuth0", type = "basic",
                                basic = BasicAuthenticationConfiguration("username", "password")),
                        AuthenticatorConfiguration("unittestAuth1", type = "OAuth2XSUAA", oauth2xsuaa = OAuth2XSUAAAuthenticationConfiguration("http://someurl.bogus", "client_id", "secret123"))
                ),
                targets = listOf(
                        Target(orgName = "unittestorg",
                                spaceName = "unittestspace",
                                applicationName = "testapp",
                                protocol = "https",
                                authenticatorId = "unittestAuth0"),
                        Target(orgName = "unittestorg",
                                spaceName = "unittestspace",
                                applicationName = "testapp2",
                                protocol = "http",
                                authenticatorId = "unittestAuth1"),
                        Target(orgName = "unittestorg",
                                spaceName = "unittestspace",
                                applicationName = "testapp3",
                                protocol = "http") // note no authenticatorId specified
                )
        )
        subject = AuthenticatorController(promregatorConfiguration)
    }

    @Test
    fun testDefaultConfigurationCheck() {
        assertThat(subject).isNotNull
        val auth0 = subject.getAuthenticationEnricherById("unittestAuth0")
        assertThat(auth0).isInstanceOf(BasicAuthenticationEnricher::class.java)
        val auth1 = subject.getAuthenticationEnricherById("unittestAuth1")
        assertThat(auth1).isInstanceOf(OAuth2XSUAAEnricher::class.java)
        val auth2 = subject.getAuthenticationEnricherById("somethingInvalid")
        assertThat(auth2).isNull()
        val auth3 = subject.getAuthenticationEnricherById(null)
        assertThat(auth3).isNull()
        val targets = promregatorConfiguration.targets
        val target0 = targets[0]
        assertThat("testapp").isEqualTo(target0.applicationName) // only as safety for this test (not really a test subject)
        assertThat(auth0).isEqualTo(subject.getAuthenticationEnricherByTarget(target0))
        val target1 = targets[1]
        assertThat("testapp2").isEqualTo(target1.applicationName) // only as safety for this test (not really a test subject)
        assertThat(auth1).isEqualTo(subject.getAuthenticationEnricherByTarget(target1))
        assertThat(subject.getAuthenticationEnricherByTarget(Target())).isInstanceOf(NullEnricher::class.java)
        assertThat(subject.getAuthenticationEnricherByTarget(null)).isInstanceOf(NullEnricher::class.java)
    }
}