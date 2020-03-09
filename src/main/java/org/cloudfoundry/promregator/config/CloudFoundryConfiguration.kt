package org.cloudfoundry.promregator.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties(prefix = "cf")
class CloudFoundryConfiguration(
        apiHost: String? = null,
        username: String? = null,
        password: String? = null,
        apiPort: Int = 443,
        skipSslValidation: Boolean = false,
        proxy: ProxyConfig? = null,
        connectionPool: ConnectionPoolConfig = ConnectionPoolConfig(),
        threadPool: ThreadPoolConfig = ThreadPoolConfig(),
        val request: RequestConfig = RequestConfig(),
        val api: MutableMap<String, ApiConfig> = mutableMapOf(),
        val watchdog: WatchDogConfig = WatchDogConfig(),
        val cache: CacheConfig = CacheConfig(),
        val internal: InternalConfig = InternalConfig()
) {

    init {
        api.forEach{ (key, config) -> config.id = key} //tell each config about their own id

        if (api[DEFAULT_API] == null && apiHost != null && username != null && password != null) api[DEFAULT_API] = ApiConfig(
                host = apiHost,
                username = username,
                password = password,
                proxy = proxy,
                port = apiPort,
                skipSslValidation = skipSslValidation,
                connectionPool = connectionPool,
                threadPool = threadPool
        )
    }

    companion object {
        const val DEFAULT_API = "__default__"
    }

}

data class InternalConfig(
        val preCheckAPIVersion: Boolean = true
)

data class CacheConfig(
        val timeout: CacheTimeoutConfig = CacheTimeoutConfig(),
        val expiry: CacheExpiryConfig = CacheExpiryConfig()
)

data class CacheExpiryConfig(
        val org: Int = 120,
        val space: Int = 120,
        val application: Int = 120
)

data class CacheTimeoutConfig(
        val org: Int = 3600,
        val space: Int = 3600,
        val application: Int = 300
)

data class WatchDogConfig(
    val enabled: Boolean = false,
    val timeout: Int = 2500,
    val restartCount: Int? = null
)

data class ApiConfig(
        val host: String,
        var id: String = host,
        val username: String,
        val password: String,
        val proxy: ProxyConfig? = null,
        val port: Int = 443,
        val skipSslValidation: Boolean = false,
        val connectionPool: ConnectionPoolConfig = ConnectionPoolConfig(),
        val threadPool: ThreadPoolConfig = ThreadPoolConfig()
)

data class ProxyConfig(
        val host: String? = null,
        val port: Int? = null
)

data class RequestConfig(
        val timeout: TimeoutConfig = TimeoutConfig(),
        val cacheDuration: Duration = Duration.ofSeconds(30)
)

data class TimeoutConfig(
        val org: Int = 10_000,
        val space: Int = 10_000,
        val appInSpace: Int = 10_000,
        val appSummary: Int = 10_000
)

data class ConnectionPoolConfig(
        val size: Int? = null
)

data class ThreadPoolConfig(
        val size: Int? = null
)


