package org.cloudfoundry.promregator.discovery

import org.cloudfoundry.promregator.cfaccessor.CFApiClients
import org.cloudfoundry.promregator.config.ApiConfig
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.ALL
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.DEFAULT_ID
import org.cloudfoundry.promregator.config.Target
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val LOG = mu.KotlinLogging.logger { }

data class ConsulService(
        val node: String, //Promregator node
        val datacenter: String, // CF API
        val taggedAddresses: Map<String, String> = mapOf(),
        val nodeMeta: Map<String, String> = mapOf(),
        val serviceAddress: String, //Promregator address for scraping
        val serviceId: String, // CF Application ID
        val serviceName: String, // CF Application name
        val servicePort: Int,
        val serviceMeta: Map<String, String> = mapOf(),
        val serviceTags: List<String> = listOf(),
        val namespace: String // CF Namespaces
)

@RestController
class ConsulDiscoveryFacade(
        @Value("\${promregator.discovery.hostname:#{null}}") private val myHostname: String? = null,
        @Value("\${promregator.discovery.port:#{null}}") private val myPort: Int? = null,
        @Value("\${promregator.discovery.cache.duration:300s}") private val discoveryCacheDuration: Duration,
        val discoverer: CachedDiscoverer,
        val cf: CloudFoundryConfiguration,
        val apiClients: CFApiClients
) {
    private fun String.decodeBase64() = String(Base64.getDecoder().decode(this))

    @GetMapping("/v1/catalog/services")
    fun consulServices(resp: HttpServletResponse,
                       req: HttpServletRequest,
                       @RequestParam dc: String?,
                       @RequestParam("node-meta") nodeMeta: List<String>): Mono<Map<String, List<String>>> {
        resp.setHeader("X-Consul-Index", Instant.now().toEpochMilli().toString())
        val apiConfig = createConfigFromBasicAuthOrConfig(dc, req.getHeader("Authorization"))
        val targets = parseTargetConfig(apiConfig, nodeMeta)

        return discoverer.discover(apiConfig.id, targets)
                .map { results ->
                    results
                            .map { it.value.target.applicationName }
                            .distinct()
                            .map { it to listOf<String>() }
                            .toMap()
                }
    }

    private fun createConfigFromBasicAuthOrConfig(dc: String?, authHeader: String?): ApiConfig {
        val apiId = dc ?: cf.api.keys.firstOrNull() ?: throw IllegalArgumentException("neither datacenter or api config found, configure either to use discovery")
        val apiConfig = cf.api[apiId]
        if(apiConfig != null) return apiConfig

        val basicAuthPrefix = "Basic "
        val (username, password) = authHeader?.substring(basicAuthPrefix.length)?.decodeBase64()
                ?.split(":") ?: throw AccessDeniedException("Discovery requires cloudfoundry credentials provided by basic auth")
        return ApiConfig(apiId, apiId, username, password)
                .also { apiClients.addClient(apiId, it) }
    }

    @GetMapping("/v1/catalog/service/{serviceName}")
    fun consulServicesSingle(resp: HttpServletResponse,
                             req: HttpServletRequest,
                             @PathVariable serviceName: String,
                             @RequestParam dc: String?,
                             @RequestParam("node-meta") nodeMeta: List<String>): Mono<List<ConsulService>> {
        val localHostname: String = this.myHostname ?: req.localName
        val localPort: Int = this.myPort ?: req.localPort
        val apiConfig = createConfigFromBasicAuthOrConfig(dc, req.getHeader("Authorization"))
        val targets = parseTargetConfig(apiConfig, nodeMeta)

        resp.setHeader("X-Consul-Index", Instant.now().toEpochMilli().toString())
        return discoverer.discover(apiConfig.id, targets).map { results ->
            results
                    .filter { it.value.target.applicationName == serviceName }
                    .map { (hash, instance) ->
                        ConsulService(
                                node = localHostname,
                                datacenter = instance.target.originalTarget.api,
                                serviceAddress = localHostname,
                                serviceId = instance.applicationId,
                                serviceName = instance.target.applicationName,
                                servicePort = localPort,
                                namespace = instance.target.spaceName,
                                serviceMeta = mapOf(
                                        "metrics_path" to "/v2/singleTargetScraping/${instance.target.applicationName}/${instance.instanceNumber}/$hash",
                                        "cf_api" to instance.target.originalTarget.api,
                                        "cf_orgName" to (instance.target.orgName ?: ""),
                                        "cf_spaceName" to (instance.target.spaceName ?: ""),
                                        "cf_instanceNumber" to instance.instanceNumber
                                )
                        )
                    }
        }
    }

    private fun parseTargetConfig(api: ApiConfig, nodeMeta: List<String>): List<Target> {
        val nodeMetaMap = nodeMeta.map { it.split(":") }.filter { it.size == 2 }.map{ it[0] to it[1]}.toMap()
        return listOf(Target(
                api = api.id,
                orgName = nodeMetaMap["orgName"],
                orgRegex = nodeMetaMap["orgRegex"],
                spaceName = nodeMetaMap["spaceName"],
                spaceRegex = nodeMetaMap["spaceRegex"],
                applicationName = nodeMetaMap["applicationName"],
                applicationRegex = nodeMetaMap["applicationRegex"],
                path = nodeMetaMap["path"] ?: "/metrics",
                protocol = nodeMetaMap["protocol"] ?: "https",
                authenticatorId = nodeMetaMap["authenticatorId"],
                preferredRouteRegex = nodeMetaMap["preferredRouteRegex"]?.let { it.split(",")} ?: listOf()
        ))
    }

    @GetMapping("/v1/agent/self")
    fun consulDiscovery() = mapOf(
            "Config" to mapOf(
                    "Datacenter" to ALL
            )
    )
}
