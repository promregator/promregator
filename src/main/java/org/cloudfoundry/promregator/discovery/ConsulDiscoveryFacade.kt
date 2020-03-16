package org.cloudfoundry.promregator.discovery

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
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
        val discoverer: CachedDiscoverer
) {
    @GetMapping("/v1/catalog/services")
    fun consulServices(resp: HttpServletResponse): Mono<Map<String, List<String>>> {
        resp.setHeader("X-Consul-Index", Instant.now().toEpochMilli().toString())
        return discoverer.discover()
                .map { results ->
                    results
                            .map { it.value.target.applicationName }
                            .distinct()
                            .map { it to listOf<String>() }
                            .toMap()
                }
    }

    @GetMapping("/v1/catalog/service/{serviceName}")
    fun consulServicesSingle(resp: HttpServletResponse,
                             @PathVariable serviceName: String): Mono<List<ConsulService>> {
        val localHostname: String = this.myHostname ?: ""// request.localName
        val localPort: Int = this.myPort ?: 0 // request.localPort

        resp.setHeader("X-Consul-Index", Instant.now().toEpochMilli().toString())
        return discoverer.discover().map { results ->
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

    @GetMapping("/v1/agent/self")
    fun consulDiscovery() = mapOf(
            "Config" to mapOf(
                    "Datacenter" to "N/A"
            )
    )
}
