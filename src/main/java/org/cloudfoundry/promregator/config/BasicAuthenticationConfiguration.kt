package org.cloudfoundry.promregator.config

data class BasicAuthenticationConfiguration(
    var username: String,
    var password: String
)