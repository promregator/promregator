package org.cloudfoundry.promregator.lite.config

import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration.Companion.DEFAULT_ID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private val log = mu.KotlinLogging.logger {  }

data class CfTarget(
        val api: String = DEFAULT_ID,
        val orgName: String? = null,
        val orgRegex: String? = null,
        val spaceName: String? = null,
        val spaceRegex: String? = null,
        val applicationName: String? = null,
        val applicationRegex: String? = null,
        val path: String = "/metrics",
        val kubernetesAnnotations: Boolean = false,
        val protocol: String = "https",
        val authenticatorId: String? = null,
        val preferredRouteRegex: List<String> = listOf(),
        val internalRoutePort: Int = 0,
        var overrideRouteAndPath: String? = null
) {
    val preferredRouteRegexPattern: List<Pattern>


    init {
        if ("http" != protocol && "https" != protocol) {
            throw InvalidTargetProtocolSpecifiedError(String.format("Invalid configuration: Target attempted to be configured with non-http(s) protocol: %s", protocol))
        }

        preferredRouteRegexPattern = preferredRouteRegex.mapNotNull { routeRegex ->
            try {
                Pattern.compile(routeRegex)
            } catch (e: PatternSyntaxException) {
                log.warn(e) { "Invalid preferredRouteRegex '$routeRegex' detected. Fix your configuration; until then, the regex will be ignored" }
                null
            }
        }
    }

}
