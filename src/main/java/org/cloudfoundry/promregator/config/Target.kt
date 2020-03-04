package org.cloudfoundry.promregator.config

import mu.KotlinLogging
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.DEFAULT_ID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private val logger = KotlinLogging.logger { }

data class Target(
        val api: String = DEFAULT_ID ,
        val orgName: String? = null,
        val orgRegex: String? = null,
        val spaceName: String? = null,
        val spaceRegex: String? = null,
        val applicationName: String? = null,
        val applicationRegex: String? = null,
        val path: String = "/metrics",
        val protocol: String = "https",
        val authenticatorId: String? = null,
        val preferredRouteRegex: List<String> = listOf(),
        val preferredRouteRegexPatterns: List<Pattern> = cachePreferredRouteRegexPatterns(preferredRouteRegex)
) {
    init {
        if ("http" != protocol && "https" != protocol) {
            throw InvalidTargetProtocolSpecifiedError("Invalid configuration: Target attempted to be configured with non-http(s) protocol: $protocol")
        }
    }

    companion object {
        private fun cachePreferredRouteRegexPatterns(regexStringList: List<String>): List<Pattern> {
            return regexStringList.mapNotNull {
                try {
                    Pattern.compile(it)
                } catch (e: PatternSyntaxException) {
                    logger.warn(e) { "Invalid preferredRouteRegex '$it' detected. Fix your configuration; until then, the regex will be ignored" }
                    null
                }
            }
        }
    }
}