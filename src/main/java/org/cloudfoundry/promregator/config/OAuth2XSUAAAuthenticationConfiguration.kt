package org.cloudfoundry.promregator.config

data class OAuth2XSUAAAuthenticationConfiguration(
    var tokenServiceURL: String,
    var client_id: String,
    var client_secret: String,
    var scopes: String? = null
)