package org.cloudfoundry.promregator.lite.config

import org.cloudfoundry.promregator.config.AbstractOAuth2XSUAAAuthenticationConfiguration
import org.cloudfoundry.promregator.config.OAuth2XSUAABasicAuthenticationConfiguration
import org.cloudfoundry.promregator.config.OAuth2XSUAACertificateAuthenticationConfiguration
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration.Companion.DEFAULT_ID
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "promregator")
data class PromregatorConfiguration(
        val targets: List<Target> = ArrayList(),
        val authenticator: AuthenticatorConfiguration = AuthenticatorConfiguration(),
        val targetAuthenticators: List<TargetAuthenticatorConfiguration> = ArrayList(),
        val internal: InternalConfig = InternalConfig(),
) {
    companion object {
        const val DEFAULT_ID = "__default__"
    }
}

data class InternalConfig(
        val preCheckAPIVersion: Boolean = true,
)

open class AuthenticatorConfiguration(
) {
    var type: String? = null
    var basic: BasicAuthenticationConfiguration? = null
    var oauth2xsuaa: OAuth2XSUAABasicAuthenticationConfiguration? = null
    var oauth2xsuaaBasic: OAuth2XSUAABasicAuthenticationConfiguration? = null
    var oauth2xsuaaCertificate: OAuth2XSUAACertificateAuthenticationConfiguration? = null
}

class BasicAuthenticationConfiguration(
        val username: String,
        val password: String,
)

class OAuth2XSUAAAuthenticationConfiguration {
    var tokenServiceURL: String? = null
    var client_id: String? = null
    var client_secret: String? = null
    var scopes: String? = null
}

class OAuth2XSUAABasicAuthenticationConfiguration : AbstractOAuth2XSUAAAuthenticationConfiguration() {
    var tokenServiceURL: String? = null
    var client_secret: String? = null
}

class OAuth2XSUAACertificateAuthenticationConfiguration : AbstractOAuth2XSUAAAuthenticationConfiguration() {
    var tokenServiceCertURL: String? = null
    var client_certificates: String? = null
    var client_key: String? = null
}

class TargetAuthenticatorConfiguration : AuthenticatorConfiguration() {
    var id: String = DEFAULT_ID
}
