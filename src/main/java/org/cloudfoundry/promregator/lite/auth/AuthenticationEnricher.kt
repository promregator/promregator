package org.cloudfoundry.promregator.lite.auth

import org.springframework.http.HttpHeaders

interface AuthenticationEnricher {
    fun enrichWithAuthentication(headers: HttpHeaders)
}
