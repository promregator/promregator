package org.cloudfoundry.promregator.config

import mu.KotlinLogging
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.DEFAULT_ID
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private val logger = KotlinLogging.logger { }

@ConstructorBinding
@ConfigurationProperties(prefix = "promregator")
data class PromregatorConfiguration(
        val workaround: WorkAroundConfig = WorkAroundConfig(),
        val simulation: SimulationConfig = SimulationConfig(),
        val reactor: ReactorConfig = ReactorConfig(),

        val targets: List<Target> = listOf(),
        val targetAuthenticators: List<AuthenticatorConfiguration> = listOf(),
        val scraping: ScrapingConfig = ScrapingConfig(),
        val metrics: MetricsConfig = MetricsConfig(),
        val discoverer: DiscovererConfig = DiscovererConfig(),
        val discovery: DiscoveryConfig = DiscoveryConfig(),
        val endpoint: EndpointConfig = EndpointConfig(),
        val cache: PromCacheConfig = PromCacheConfig(),
        val internal: InternalConfig = InternalConfig()
){
        companion object {
                const val DEFAULT_ID = "__default__"
                const val ALL = "__all__"
        }
}

data class Target(
        val api: String = DEFAULT_ID,
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

data class AuthenticatorConfiguration(
        var id: String = DEFAULT_ID,
        var type: String? = null,
        var basic: BasicAuthenticationConfiguration? = null,
        var oauth2xsuaa: OAuth2XSUAAAuthenticationConfiguration? = null
)

data class PromCacheConfig(
        val invalidate: PromInvalidateCacheConfig = PromInvalidateCacheConfig()
)

data class PromInvalidateCacheConfig(
        val auth: InboundAuthorizationMode = InboundAuthorizationMode.NONE
)

data class EndpointConfig(
        val auth: InboundAuthorizationMode = InboundAuthorizationMode.NONE
)

data class DiscovererConfig(
        val timeout: Int = 600
)

data class DiscoveryConfig(
        val hostname: String? = null,
        val port: Int = 0,
        val ownMetricsEndpoint: Boolean = true,
        val auth: InboundAuthorizationMode = InboundAuthorizationMode.NONE,
        val cache: DiscoveryCacheConfig = DiscoveryCacheConfig()
)

data class DiscoveryCacheConfig (
        val duration: Duration = Duration.ofSeconds(300)
)

data class MetricsConfig(
        val requestLatency: Boolean = false,
        val auth: InboundAuthorizationMode = InboundAuthorizationMode.NONE
)

data class ScrapingConfig(
        val proxy: ScrapingProxyConfig = ScrapingProxyConfig(),
        val maxProcessingTime: Int = 5000,
        val connectionTimeout: Int = 5000,
        val socketReadTimeout: Int = 5000
)

data class ScrapingProxyConfig(
        val host: String? = null,
        val port: Int = 0
        )

data class SimulationConfig(
        val enabled: Boolean = false,
        val instances: Int = 10
)

data class ReactorConfig(
        val debug: Boolean = false
)

data class WorkAroundConfig(
        val dnscache: WorkAroundDnsCacheConfig = WorkAroundDnsCacheConfig()
)

data class WorkAroundDnsCacheConfig(
        val timeout: Int = -1
)

