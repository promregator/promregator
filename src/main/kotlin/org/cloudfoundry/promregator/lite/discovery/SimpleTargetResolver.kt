package org.cloudfoundry.promregator.lite.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.cloudfoundry.client.v2.routes.Route
import org.cloudfoundry.promregator.lite.config.CfTarget
import org.springframework.stereotype.Component
import java.util.*
import kotlin.text.RegexOption.IGNORE_CASE


data class OrgResponse(
        val api: String,
        val id: String,
        val name: String
)

data class SpaceResponse(
        val org: OrgResponse,
        val spaceName: String,
        val spaceId: String,
        val domains: List<DomainResponse>,
)

data class SpaceSummaryAppResponse(
        val space: SpaceResponse,
        val appName: String,
        val appSummary: AppSummary
)

data class AppSummary(
        val instances: Int,
        val urls: List<String>,
        val routes: List<Route>,
)

data class DomainResponse(
        val orgId: String,
        val domainId: String,
        val isInternal: Boolean,
        val namex: String,
)

data class AppResponse(
        val appId: String,
        val appName: String,
        val space: SpaceResponse,
        val isScrapable: Boolean,
        val annotationScrape: Boolean,
        val annotationMetricsPath: String? = null,
        val originalTarget: CfTarget? = null,
        val instances: Int = 0,
        val urls: List<String> = listOf(),
        val routes: List<DomainResponse> = listOf(),
) {
    val protocol: String
        get() = originalTarget?.protocol ?: "https"
    val metricsPath: String
        get() = annotationMetricsPath ?: originalTarget?.path ?: "/metrics"
}

private val log = mu.KotlinLogging.logger { }

@Component
class SimpleTargetResolver(
) {

    fun resolveTargets(configTargetsToMatch: List<CfTarget>): List<AppResponse> {
        log.warn { "Resolve targets called but not fully implemented yet" }
        return listOf()
    }

    companion object {
        const val PROMETHEUS_IO_SCRAPE = "prometheus.io/scrape"
        const val PROMETHEUS_IO_PATH = "prometheus.io/path"
        val LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON: Locale = Locale.ENGLISH
    }
}

