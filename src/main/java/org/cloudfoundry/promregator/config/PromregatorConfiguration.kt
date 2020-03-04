package org.cloudfoundry.promregator.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.util.*

@ConstructorBinding
@ConfigurationProperties(prefix = "promregator", ignoreInvalidFields = true)
data class PromregatorConfiguration(

        val workaround: WorkAroundConfig = WorkAroundConfig(),
        val simulation: SimulationConfig = SimulationConfig(),
        val reactor: ReactorConfig = ReactorConfig(),

        var targets: List<Target> = ArrayList(),
        var authenticator: AuthenticatorConfiguration = AuthenticatorConfiguration(),
        var targetAuthenticators: List<AuthenticatorConfiguration> = ArrayList(),
        val scraping: ScrapingConfig = ScrapingConfig(),
        val metrics: MetricsConfig = MetricsConfig(),
        val discoverer: DiscovererConfig = DiscovererConfig(),
        val discovery: DiscoveryConfig = DiscoveryConfig(),
        val endpoint: EndpointConfig = EndpointConfig(),
        val cache: PromCacheConfig = PromCacheConfig()
)

open class AuthenticatorConfiguration(
        var id: String = "__default__",
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
        val auth: InboundAuthorizationMode = InboundAuthorizationMode.NONE
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