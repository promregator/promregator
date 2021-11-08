package org.cloudfoundry.promregator.lite.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties(prefix = "cf")
data class CloudFoundryConfig(
        val api_host: String? = null,
        val username: String? = null,
        val password: String? = null,
        val apiPort: Int = 443,
        val skipSslValidation: Boolean = false,
        val proxy: ProxyConfig? = null,
        val connectionPool: ConnectionPoolConfig = ConnectionPoolConfig(),
        val threadPool: ThreadPoolConfig = ThreadPoolConfig(),
        val apis: MutableMap<String, ApiConfig> = mutableMapOf(),
        val request: RequestConfig = RequestConfig(),
        val client: ClientConfig = ClientConfig()
) {
    init {
        if (apis[DEFAULT_API] == null && api_host != null && username != null && password != null) apis[DEFAULT_API] = ApiConfig(
                host = api_host,
                username = username,
                password = password,
                proxy = proxy,
                port = apiPort,
                skipSslValidation = skipSslValidation,
        )
    }

    companion object {
        const val DEFAULT_API = "__default__"
    }
}

data class ClientConfig(
        val cacheDuration: Duration = Duration.ofMinutes(10)
)

data class RequestConfig(
        val rateLimit: Double = 0.0,
        val backoff: Long = 500,
        val timeout: TimeoutConfig = TimeoutConfig(),
        val cacheDuration: Duration = Duration.ofSeconds(30)
)

data class ApiConfig(
        val host: String,
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

data class TimeoutConfig(
        val org: Int = 2500,
        val space: Int = 2500,
        val appInSpace: Int = 2500,
        val appSummary: Int = 4000,
        val domain: Int = 2500,
)

data class ConnectionPoolConfig(
        val size: Int? = null
)

data class ThreadPoolConfig(
        val size: Int? = null
)
