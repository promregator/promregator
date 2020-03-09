package org.cloudfoundry.promregator.cfaccessor

import org.apache.http.conn.util.InetAddressUtils
import org.cloudfoundry.client.v2.info.GetInfoRequest
import org.cloudfoundry.client.v2.info.GetInfoResponse
import org.cloudfoundry.promregator.config.ApiConfig
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration
import org.cloudfoundry.promregator.config.ConfigurationException
import org.cloudfoundry.reactor.ConnectionContext
import org.cloudfoundry.reactor.DefaultConnectionContext
import org.cloudfoundry.reactor.ProxyConfiguration
import org.cloudfoundry.reactor.TokenProvider
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.regex.Pattern

private val log = mu.KotlinLogging.logger {  }

@Component
class CFApiClients(
        val cf: CloudFoundryConfiguration
) {
    private val PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE)
    private val cloudFoundryClients: MutableMap<String, ReactorCloudFoundryClient> = ConcurrentHashMap()
    init {
        createClients()
    }

    fun addClient(id: String, apiConfig: ApiConfig) {
        resetCloudFoundryClient(id, apiConfig)
    }

    fun getClient(id: String) = cloudFoundryClients.get(id) ?: throw IllegalStateException("CF Client with id $id doesn't exist yet")

    fun forEach(action: (Map.Entry<String, ReactorCloudFoundryClient>) -> Unit) {
        cloudFoundryClients.forEach { action(it) }
    }

    private fun resetCloudFoundryClient(api: String, apiConfig: ApiConfig) {
        try {
            val proxyConfiguration: ProxyConfiguration? = this.proxyConfiguration(apiConfig)
            val connectionContext: DefaultConnectionContext? = this.connectionContext(apiConfig, proxyConfiguration)
            val tokenProvider: PasswordGrantTokenProvider? = this.tokenProvider(apiConfig)
            this.cloudFoundryClients[api] = this.cloudFoundryClient(connectionContext, tokenProvider)
        } catch (e: ConfigurationException) {
            log.error("Restarting Cloud Foundry Client failed due to Configuration Exception raised", e)
        }
    }

    private fun cloudFoundryClient(connectionContext: ConnectionContext?, tokenProvider: TokenProvider?): ReactorCloudFoundryClient {
        return ReactorCloudFoundryClient.builder().connectionContext(connectionContext).tokenProvider(tokenProvider).build()
    }


    private fun createClients() {
        cf.api.keys.forEach(Consumer<String> { api: String -> reset(api) })
        if (cf.internal.preCheckAPIVersion) {
            val request = GetInfoRequest.builder().build()
            cloudFoundryClients.forEach { entry: Map.Entry<String, ReactorCloudFoundryClient> ->
                val api = entry.key
                val getInfo = entry.value.info()[request].block()
                        ?: throw RuntimeException("Error connecting to CF api '$api'")
                // NB: This also ensures that the connection has been established properly...
                log.info("Target CF platform ({}) is running on API version {}", api, getInfo.apiVersion)
            }
        }
    }


    fun reset(api: String) {
        val apiConfig: ApiConfig = this.cf.api[api] ?: throw IllegalStateException("Api not found: $api")
        resetCloudFoundryClient(api, apiConfig)
    }

    private fun connectionContext(apiConfig: ApiConfig, proxyConfiguration: ProxyConfiguration?): DefaultConnectionContext? {
        if (PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(apiConfig.host).find()) {
            throw ConfigurationException("cf.api_host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead")
        }
        var connctx = DefaultConnectionContext.builder()
                .apiHost(apiConfig.host)
                .skipSslValidation(apiConfig.skipSslValidation)
        if (proxyConfiguration != null) {
            connctx = connctx.proxyConfiguration(proxyConfiguration)
        }
        if (apiConfig.connectionPool.size != null) {
            connctx = connctx.connectionPoolSize(apiConfig.connectionPool.size)
        }
        if (apiConfig.threadPool.size != null) {
            connctx = connctx.threadPoolSize(apiConfig.threadPool.size)
        }
        return connctx.build()
    }

    private fun tokenProvider(apiConfig: ApiConfig): PasswordGrantTokenProvider? {
        return PasswordGrantTokenProvider.builder().password(apiConfig.password).username(apiConfig.username).build()
    }

    private fun proxyConfiguration(apiConfig: ApiConfig): ProxyConfiguration? {
        var effectiveProxyHost: String? = null
        var effectiveProxyPort = 0
        if (apiConfig.proxy != null && apiConfig.proxy.host != null && apiConfig.proxy.port != null) { // used the new way of defining proxies
            effectiveProxyHost = apiConfig.proxy.host
            effectiveProxyPort = apiConfig.proxy.port
        }
        if (effectiveProxyHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(effectiveProxyHost).find()) {
            throw ConfigurationException("Configuring of cf.proxyHost or cf.proxy.host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead")
        }
        return if (effectiveProxyHost != null && effectiveProxyPort != 0) {
            var proxyIP: String? = null
            proxyIP = if (!InetAddressUtils.isIPv4Address(effectiveProxyHost) && !InetAddressUtils.isIPv6Address(effectiveProxyHost)) { /*
                     * NB: There is currently a bug in io.netty.util.internal.SocketUtils.connect()
                     * which is called implicitly by the CF API Client library, which leads to the effect
                     * that a hostname for the proxy isn't resolved. Thus, it is only possible to pass
                     * IP addresses as proxy names.
                     * To work around this issue, we manually perform a resolution of the hostname here
                     * and then feed that one to the CF API Client library...
                     */
                try {
                    val ia = InetAddress.getByName(effectiveProxyHost)
                    ia.hostAddress
                } catch (e: UnknownHostException) {
                    throw ConfigurationException(String.format("The proxy host '%s' cannot be resolved to an IP address; is there a typo in your configuration?", effectiveProxyHost), e)
                }
            } else { // the address specified is already an IP address
                effectiveProxyHost
            }
            ProxyConfiguration.builder().host(proxyIP).port(effectiveProxyPort).build()
        } else {
            null
        }
    }

    fun check(api: String): Mono<GetInfoResponse> {
        val request = GetInfoRequest.builder().build()
        return cloudFoundryClients[api]?.info()?.get(request) ?: Mono.error(IllegalArgumentException("No api client for api $api found"))

    }

}