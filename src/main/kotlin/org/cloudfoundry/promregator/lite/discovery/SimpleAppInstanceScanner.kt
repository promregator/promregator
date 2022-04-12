package org.cloudfoundry.promregator.lite.discovery

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.regex.Pattern

private val log = mu.KotlinLogging.logger {  }

@Component
class SimpleAppInstanceScanner(
        @Value("\${promregator.defaultInternalRoutePort:8080}") private val defaultInternalRoutePort: Int,
) {

    /**
     * An instance provides a mapping from a target (provided by configuration)
     * to an exact descriptor consisting of the Access URL and the instance identifier,
     * which can be used for scraping.
     */
    data class Instance(
            val target: AppResponse,
            val instanceId: String,
            val accessUrl: String,
            val isInternal: Boolean = false
    ) {
        val instanceNumber: String get() = instanceId.split(":")[1]
        val applicationId: String get() = instanceId.split(":")[0]
    }


    fun determineInstancesFromTargets(targets: List<AppResponse>,
                                      applicationIdFilter: (String) -> Boolean = { true },
                                      instanceFilter: (Instance) -> Boolean = { true }): List<Instance> {

        log.warn { "determineInstancesFromTargets called but not yet implemented" }
        return listOf()
    }

}
