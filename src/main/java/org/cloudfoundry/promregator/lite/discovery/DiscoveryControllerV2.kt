package org.cloudfoundry.promregator.lite.discovery

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

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
) {
    @get:JsonGetter("__metrics_path__")
    val metricsPath: String
        get() = targetPath
}

private val log = mu.KotlinLogging.logger {}

@RestController
class DiscoveryControllerV2(
//        private val discoveryService: DiscoveryService,
        @Value("\${promregator.discovery.hostname:#{null}}") private val promHostname: String?,
        @Value("\${promregator.discovery.port:0}") val promPort: Int,
        @Value("\${promregator.discovery.ownMetricsEndpoint:true}") private val promregatorMetricsEndpoint: Boolean,
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
            log.warn { "V2 Discovery is incomplete, but callable" }
            listOf()
        }
    }
}
