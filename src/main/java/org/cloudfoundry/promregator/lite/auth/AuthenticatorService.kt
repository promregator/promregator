package org.cloudfoundry.promregator.lite.auth

//import org.cloudfoundry.promregator.auth.BasicAuthenticationEnricher
//import org.cloudfoundry.promregator.auth.OAuth2XSUAAEnricher
import org.cloudfoundry.promregator.lite.config.AuthenticatorConfiguration
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration.Companion.DEFAULT_ID
import org.springframework.stereotype.Service
import org.cloudfoundry.promregator.auth.AuthenticationEnricher as AuthenticationEnricher1

private val log = mu.KotlinLogging.logger { }

/**
 * This class controls the target-dependent set of AuthenticationEnrichers
 */
@Service
class AuthenticatorService(promregatorConfiguration: PromregatorConfiguration) {
    private val mapping: MutableMap<String, AuthenticationEnricher> = HashMap()
    private var globalAuthenticationEnricher: AuthenticationEnricher

    /**
     * retrieves the AuthenticationEnricher identified by its ID (note: this is
     * *NOT* the target!)
     *
     * @param authId
     * the authenticatorId as specified in the configuration definition
     * @return the AuthenticationEnricher, which is associated to the given identifier, or `null` in case
     * that no such AuthenticationEnricher exists.
     */
    fun getAuthenticationEnricherById(authId: String?): AuthenticationEnricher {
        if (authId == null) return globalAuthenticationEnricher
        return mapping.getOrDefault(authId, globalAuthenticationEnricher)
    }

    init {
        val authConfig = promregatorConfiguration.targetAuthenticators
                .firstOrNull { it.id == DEFAULT_ID }
        globalAuthenticationEnricher = create(authConfig) ?: NullEnricher()

        for (tac in promregatorConfiguration.targetAuthenticators) {
            val id = tac.id
            val ae = create(tac)
            if (ae != null && id != null) {
                mapping[id] = ae
            }
        }
    }

    private fun create(authConfig: AuthenticatorConfiguration?): AuthenticationEnricher? {
//        var ae: AuthenticationEnricher? = null
//        val type = authConfig?.type
//        if ("OAuth2XSUAA".equals(type, ignoreCase = true)) {
//            ae = OAuth2XSUAAEnricher(authConfig?.oauth2xsuaa)
//        } else if ("none".equals(type, ignoreCase = true) || "null".equals(type, ignoreCase = true)) {
//            ae = NullEnricher()
//        } else if ("basic".equals(type, ignoreCase = true)) {
//            val basic = authConfig?.basic ?:
//                                error("auth.basic type is to be used, but config is missing")
//
//            ae = BasicAuthenticationEnricher(basic)
//        } else {
//            log.warn { "Authenticator type $type is unknown; skipping" }
//        }
//        return ae

        var ae: AuthenticationEnricher? = null

        val type = authConfig?.type
        if ("OAuth2XSUAA".equals(type, ignoreCase = true)) {
            log.warn { "You are using deprecated authentication configuration type 'OAuth2XSUAA'. Switch to 'OAuth2XSUAABasic' instead." }
            ae = OAuth2XSUAAEnricher(authConfig?.oauth2xsuaa)
        } else if ("OAuth2XSUAABasic".equals(type, ignoreCase = true)) {
            ae = OAuth2XSUAAEnricher(authConfig?.oauth2xsuaaBasic)
        } else if ("OAuth2XSUAACertificate".equals(type, ignoreCase = true)) {
            ae = OAuth2XSUAAEnricher(authConfig?.oauth2xsuaaCertificate)
        } else if ("none".equals(type, ignoreCase = true) || "null".equals(type, ignoreCase = true)) {
            ae = NullEnricher()
        } else if ("basic".equals(type, ignoreCase = true)) {
            val basic = authConfig?.basic ?:
                                error("auth.basic type is to be used, but config is missing")
            ae = BasicAuthenticationEnricher(basic)
        } else {
            log.warn { "Authenticator type $type is unknown; skipping" }
        }

        return ae
    }
}
