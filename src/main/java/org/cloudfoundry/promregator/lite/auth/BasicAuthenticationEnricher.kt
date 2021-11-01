package org.cloudfoundry.promregator.lite.auth

import org.cloudfoundry.promregator.lite.config.BasicAuthenticationConfiguration
import org.springframework.http.HttpHeaders

class BasicAuthenticationEnricher(private val authenticatorConfig: BasicAuthenticationConfiguration) : AuthenticationEnricher {
    override fun enrichWithAuthentication(headers: HttpHeaders) {
        headers.setBasicAuth(authenticatorConfig.username, authenticatorConfig.password)
    }
}
