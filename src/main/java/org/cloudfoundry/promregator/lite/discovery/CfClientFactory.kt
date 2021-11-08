package org.cloudfoundry.promregator.lite.discovery

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.http.conn.util.InetAddressUtils
import org.cloudfoundry.client.v2.info.GetInfoRequest
import org.cloudfoundry.promregator.lite.cfaccessor.ReactorInfoV3
import org.cloudfoundry.promregator.lite.config.ApiConfig
import org.cloudfoundry.promregator.lite.config.CloudFoundryConfig
import org.cloudfoundry.promregator.lite.config.ConfigurationException
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration
import org.cloudfoundry.reactor.ConnectionContext
import org.cloudfoundry.reactor.DefaultConnectionContext
import org.cloudfoundry.reactor.ProxyConfiguration
import org.cloudfoundry.reactor.TokenProvider
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val log = mu.KotlinLogging.logger { }

@Component
class CfClientFactory(
        private val cf: CloudFoundryConfig,
) {
    val clientCache =   Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(cf.client.cacheDuration)
            .build {api: String -> runBlocking { createCloudFoundryClient(api) } }

    fun getClient(api: String): ReactorCloudFoundryClient {
        return clientCache.get(api) ?: throw ConfigurationException("Error with client for api:$api")
    }

    private fun createCloudFoundryClient(api: String): ReactorCloudFoundryClient {
        val apiConfig = cf.apis[api] ?: throw ConfigurationException("Config for $api missing")
        val proxyConfiguration = proxyConfiguration(apiConfig)
        val connectionContext = connectionContext(apiConfig, proxyConfiguration)
        val tokenProvider = tokenProvider(apiConfig)
        return cloudFoundryClient(connectionContext, tokenProvider)
    }

    private val PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE)

    private fun connectionContext(apiConfig: ApiConfig, proxyConfiguration: ProxyConfiguration?): DefaultConnectionContext {
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

    private fun tokenProvider(apiConfig: ApiConfig): PasswordGrantTokenProvider {
        return PasswordGrantTokenProvider.builder().password(apiConfig.password).username(apiConfig.username).build()
    }

    private fun proxyConfiguration(apiConfig: ApiConfig): ProxyConfiguration? {
        val effectiveProxyHost = apiConfig.proxy?.host
        val effectiveProxyPort = apiConfig.proxy?.port ?: 0

        if (effectiveProxyHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(effectiveProxyHost).find()) {
            throw ConfigurationException("Configuring of cf.proxyHost or cf.proxy.host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead")
        }
        return if (effectiveProxyHost != null && effectiveProxyPort != 0) {
            val proxyIP = if (!InetAddressUtils.isIPv4Address(effectiveProxyHost) && !InetAddressUtils.isIPv6Address(effectiveProxyHost)) {
                /*
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
            } else {
                // the address specified is already an IP address
                effectiveProxyHost
            }
            ProxyConfiguration.builder().host(proxyIP).port(effectiveProxyPort).build()
        } else {
            null
        }
    }

    private fun cloudFoundryClient(connectionContext: ConnectionContext, tokenProvider: TokenProvider): ReactorCloudFoundryClient {
        return ReactorCloudFoundryClient.builder().connectionContext(connectionContext).tokenProvider(tokenProvider).build()
    }

    fun createClients(apis: List<String>): Map<String, ReactorCloudFoundryClient> {
        return apis.associateWith { api ->
            val client = createCloudFoundryClient(api)
            client
        }
//        if (promregatorConfiguration.internal.preCheckAPIVersion) {
//            val request = GetInfoRequest.builder().build()
//            cloudFoundryClients.forEach { (api: String, client: ReactorCloudFoundryClient) ->
//                val getInfo = client.info()[request].block()
//                        ?: throw RuntimeException("Error connecting to CF api '$api'")
//                // NB: This also ensures that the connection has been established properly...
//                log.info { "Target CF platform ($api) is running on API version ${getInfo.apiVersion}" }
//
//                // Ensures v3 API exists. The CF Java Client does not yet implement the info endpoint for V3, so we do it manually.
//                val v3Info = ReactorInfoV3(client.connectionContext, client.rootV3,
//                        client.tokenProvider, client.requestTags)
//                        .get().onErrorReturn(JsonNodeFactory.instance.nullNode()).block()
//                if (v3Info == null || v3Info.isNull) {
//                    log.warn("Unable to get v3 info endpoint of CF platform, some features will not work as expected")
//                    _isV3Enabled = false
//                } else {
//                    _isV3Enabled = true
//                }
//            }
//        }
    }

}
