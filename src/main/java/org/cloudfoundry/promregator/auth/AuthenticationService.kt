package org.cloudfoundry.promregator.auth

import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.DEFAULT_ID
import org.cloudfoundry.promregator.config.Target
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import java.util.*
import javax.annotation.PostConstruct

@Service
class AuthenticationService(
        val promregatorConfiguration: PromregatorConfiguration
) {
    private val mapping: MutableMap<String, AuthenticationEnricher> = mutableMapOf()
    private lateinit var globalAuthenticationEnricher: AuthenticationEnricher

    @PostConstruct
    private fun determineGlobalAuthenticationEnricher() {
        val authConfig = promregatorConfiguration.targetAuthenticators.firstOrNull { it.id == DEFAULT_ID}
        if (authConfig == null) {
            globalAuthenticationEnricher = NullEnricher()
            return
        }
        globalAuthenticationEnricher = AuthenticationEnricherFactory.create(authConfig) ?: NullEnricher()
    }

    @PostConstruct
    private fun loadMapFromConfiguration() {
        for (tac in promregatorConfiguration.targetAuthenticators) {
            val id = tac.id ?: throw RuntimeException("target authenticator is missing id property $tac")
            val ae: AuthenticationEnricher? = AuthenticationEnricherFactory.create(tac)
            if (ae != null) {
                mapping[id] = ae
            }
        }
    }

    /**
     * retrieves the AuthenticationEnricher identified by its ID (note: this is
     * *NOT* the target!)
     *
     * @param authId
     * the authenticatorId as specified in the configuration definition
     * @return the AuthenticationEnricher, which is associated to the given
     * identifier, or `null` in case that no such
     * AuthenticationEnricher exists.
     */
    fun getAuthenticationEnricherById(authId: String?): AuthenticationEnricher {
        return if(authId == null) globalAuthenticationEnricher else mapping[authId] ?: NullEnricher()
    }

    /**
     * retrieves the target-specific AuthenticationEnricher for a given target
     * @param target the target for which its AuthenticationEnricher shall be retrieved
     * @return the AuthenticationEnricher for the target, which was requested. If there is no
     * target-specific AuthenticationEnricher defined for the target, a fallback to the globally
     * defined AuthenticationEnricher is performed.
     */
    fun getAuthenticationEnricherByTarget(target: Target?): AuthenticationEnricher? {
        var ae: AuthenticationEnricher? = null
        if (target == null) {
            return globalAuthenticationEnricher
        }
        val authId = target.authenticatorId
        if (authId != null) {
            ae = getAuthenticationEnricherById(authId)
            if (ae != null) {
                return ae
            }
        }
        // fallback
        return globalAuthenticationEnricher
    }
}