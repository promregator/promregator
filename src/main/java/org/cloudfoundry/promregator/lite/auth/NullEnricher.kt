package org.cloudfoundry.promregator.lite.auth

import org.springframework.http.HttpHeaders

/**
 * The NullEnricher is an AuthenticationEnricher, which does not enrich
 * the HTTP request. It can be used in case an AuthenticationEnricher is required,
 * but no operation shall be performed
 *
 */
class NullEnricher : AuthenticationEnricher {
    override fun enrichWithAuthentication(headers: HttpHeaders) {
        // left blank intentionally
    }
}
