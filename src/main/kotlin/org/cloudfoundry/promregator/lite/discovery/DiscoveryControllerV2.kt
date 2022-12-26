package org.cloudfoundry.promregator.lite.discovery

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.coroutines.runBlocking
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.endpoint.EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS
import org.cloudfoundry.promregator.lite.config.PromregatorConfigurationV2
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

data class DiscoveryV2Response(
        val targets: List<String>,
        val labels: DiscoveryLabelV2,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
class DiscoveryLabelV2(
        @get:JsonGetter("__meta_promregator_target_path") val targetPath: String,
        @get:JsonGetter("__meta_promregator_target_orgName") val orgName: String? = null,
        @get:JsonGetter("__meta_promregator_target_spaceName") val spaceName: String? = null,
        @get:JsonGetter("__meta_promregator_target_applicationName") val applicationName: String? = null,
        @get:JsonGetter("__meta_promregator_target_applicationId") val applicationId: String? = null,
        @get:JsonGetter("__meta_promregator_target_instanceNumber") val instanceNumber: String? = null,
        @get:JsonGetter("__meta_promregator_target_instanceId") val instanceId: String? = null,
        @get:JsonGetter("__meta_promregator_target_api") val api: String? = null,
) {
    @get:JsonGetter("__metrics_path__")
    val metricsPath: String
        get() = targetPath
}

private val log = mu.KotlinLogging.logger {}

@RestController
class DiscoveryControllerV2(
        private val promConfig: PromregatorConfigurationV2,
        private val discoveryService: DiscoveryService,
) {

    @GetMapping("v2/discovery")
    fun discovery(request: HttpServletRequest,
                  @RequestParam(required = false, defaultValue = "false") bypassCache: Boolean,
                  @RequestParam api: String?,
                  @RequestParam org: String?,
                  @RequestParam space: String?,
                  @RequestParam application: String?
    ): List<DiscoveryV2Response> {
        return runBlocking {
            val instances = if (bypassCache) discoveryService.discover() else discoveryService.discoverCached()

            val localHostname: String = promConfig.discovery.hostname ?: request.localName
            val localPort: Int = if (promConfig.discovery.port != 0) promConfig.discovery.port else request.localPort
            val targets = listOf("$localHostname:$localPort")
            log.info { "Using scraping target ${targets.first()} in discovery response" }


            var result = instances.map { instance ->
                val path = "/v2/singleTargetMetrics" +
                        "/${instance.applicationId}/${instance.instanceNumber}"
                val dl = DiscoveryLabelV2(path,
                        instance.target.space.org.name,
                        instance.target.space.spaceName,
                        instance.target.appName,
                        instance.target.appId,
                        instance.instanceNumber,
                        instance.instanceId,
                        instance.target.space.org.api)
                DiscoveryV2Response(targets, dl)
            }

            if (promConfig.discovery.ownMetricsEndpoint) {
                // finally, also add our own metrics endpoint
                val dl = DiscoveryLabelV2(ENDPOINT_PATH_PROMREGATOR_METRICS)
                result = result + DiscoveryV2Response(targets, dl)
            }

            log.info { "Returning discovery document with ${result.size} targets" }
            result
        }
    }
}
