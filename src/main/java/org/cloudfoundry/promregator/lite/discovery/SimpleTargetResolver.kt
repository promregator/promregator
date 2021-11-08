package org.cloudfoundry.promregator.lite.discovery

import org.cloudfoundry.promregator.lite.cfaccessor.SimpleCFAccessor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.cloudfoundry.client.v2.routes.Route
import org.cloudfoundry.promregator.lite.config.CloudFoundryConfig
import org.springframework.stereotype.Component
import java.util.*
import org.cloudfoundry.promregator.lite.config.CfTarget
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient
import kotlin.text.RegexOption.IGNORE_CASE

data class OrgResponse(
        val api: String,
        val id: String,
        val name: String,
        val cfClient: ReactorCloudFoundryClient,
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
        private val cfAccessor: SimpleCFAccessor,
        private val clientFactory: CfClientFactory,
        private val cf: CloudFoundryConfig
) {

    @Suppress("SimplifyBooleanWithConstants")
    suspend fun resolveTargets(configTargetsToMatch: List<CfTarget>): List<AppResponse> = coroutineScope {
        val configTargets = configTargetsToMatch.ifEmpty { listOf(CfTarget()) }

        val cfClients = cf.apis.keys.associateWith { clientFactory.getClient(it) }

        val apps = if (cfAccessor.isV3Enabled) {
            val orgs = resolveOrgsV3(cfClients).filterOrgs(configTargets)
            val domains = orgs.resolveDomainsV3()
            val spaces = orgs.resolveSpacesV3(domains).filterSpaces(configTargets)

            spaces.resolveAppsV3()
        } else {
            val orgs = resolveOrgs(cfClients).filterOrgs(configTargets)
            val domains = orgs.resolveDomains()
            val spaces = orgs.resolveSpaces(configTargets, domains).filterSpaces(configTargets)

            val spaceSummaries = spaces.resolveSummaries()
            spaces.resolveAppsV2(spaceSummaries)
        }

        return@coroutineScope apps.map { app ->
            val target = configTargets.firstOrNull { t ->
                app.space.org.api == t.api &&
                        ((app.annotationScrape && t.kubernetesAnnotations) || t.kubernetesAnnotations == false) &&
                        ((t.applicationName == null && t.applicationRegex == null) ||
                                (t.applicationName == null && t.applicationRegex?.toRegex(IGNORE_CASE)?.matches(app.appName) == true) ||
                                (t.applicationName == app.appName && t.applicationRegex == null))
            }
            app.copy(originalTarget = target)
        }.filter {
            it.originalTarget != null &&
                    it.isScrapable
        }
    }

    private fun List<OrgResponse>.filterOrgs(configTargets: List<CfTarget>): List<OrgResponse> {
        return this.filter { org ->
            configTargets.any { target ->
                org.api == target.api &&
                        ((target.orgName == null && target.orgRegex == null) ||
                                (target.orgName == null && target.orgRegex?.toRegex(IGNORE_CASE)?.matches(org.name) == true) ||
                                (target.orgRegex == null && target.orgName == org.name))
            }
        }
    }

    private suspend fun resolveOrgs(cfClients: Map<String, ReactorCloudFoundryClient>): List<OrgResponse> = coroutineScope {
        cfClients.map { (api, client) ->
            async {
                cfAccessor.retrieveAllOrgIds(client).map {
                    OrgResponse(api, it.metadata.id, it.entity.name, client)
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun resolveOrgsV3(cfClients: Map<String, ReactorCloudFoundryClient>): List<OrgResponse> = coroutineScope {
        cfClients.map { (api, client) ->
            async {
                cfAccessor.retrieveAllOrgIdsV3(client).map {
                    OrgResponse(api, it.id, it.name, client)
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun List<OrgResponse>.resolveDomains() = coroutineScope {
        return@coroutineScope this@resolveDomains.map { org ->
            async {
                cfAccessor.retrieveAllDomains(org.cfClient, org.id)?.resources?.map { domainResource ->
                    DomainResponse(org.id, domainResource.metadata.id, domainResource.entity.internal
                            ?: false, domainResource.entity.name)
                } ?: listOf()
            }
        }.awaitAll().flatten()
    }

    private suspend fun List<OrgResponse>.resolveDomainsV3() = coroutineScope {
        return@coroutineScope this@resolveDomainsV3.map { org ->
            async {
                cfAccessor.retrieveAllDomainsV3(org.cfClient, org.id)?.resources?.map { domainResource ->
                    DomainResponse(org.id, domainResource.id, domainResource.isInternal, domainResource.name)
                } ?: listOf()
            }
        }.awaitAll().flatten()
    }

    private fun List<SpaceResponse>.filterSpaces(configTargets: List<CfTarget>): List<SpaceResponse> {
        return this.filter { space ->
            configTargets.any { target ->
                (target.spaceName == null && target.spaceRegex == null) ||
                        (target.spaceName == null && target.spaceRegex?.toRegex(IGNORE_CASE)?.matches(space.spaceName) == true) ||
                        (target.spaceName == space.spaceName && target.spaceRegex == null)
            }
        }

    }

    private suspend fun List<OrgResponse>.resolveSpacesV3(domains: List<DomainResponse>) = coroutineScope {
        return@coroutineScope this@resolveSpacesV3.map { org ->
            async {
                cfAccessor.retrieveSpaceIdsInOrgV3(org.cfClient, org.id).map { space ->
                    SpaceResponse(org, space.name, space.id, domains.filter { it.orgId == org.id })
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun List<OrgResponse>.resolveSpaces(configTargets: List<CfTarget>, domains: List<DomainResponse>) = coroutineScope {
        return@coroutineScope this@resolveSpaces.map { org ->
            async {
                cfAccessor.retrieveSpaceIdsInOrg(org.cfClient, org.id).map { space ->
                    SpaceResponse(org, space.entity.name, space.metadata.id, domains.filter { it.orgId == org.id })
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun List<SpaceResponse>.resolveSummaries() = coroutineScope {
        return@coroutineScope this@resolveSummaries.map { space ->
            async {
                cfAccessor.retrieveSpaceSummary(space.org.cfClient, space.spaceId)?.applications?.map { app ->
                    val appName = app.name.lowercase(LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON)
                    SpaceSummaryAppResponse(space, appName, AppSummary(app.instances, app.urls, app.routes))
                } ?: listOf()
            }
        }.awaitAll().flatten()
    }

    private suspend fun List<SpaceResponse>.resolveAppsV3() = coroutineScope {
        return@coroutineScope this@resolveAppsV3.map { space ->
            async {
                val appsv3 = cfAccessor.retrieveAllApplicationsInSpaceV3(space.org.cfClient, space.org.id, space.spaceId)
                val procsDefs = appsv3.map { async { cfAccessor.retrieveApplicationProcessV3(space.org.cfClient, it.id) } }
                val routesDefs = appsv3.map { async { it.id to cfAccessor.retrieveApplicationsRoutesV3(space.org.cfClient, it.id) } }
                val procs = procsDefs.awaitAll() // Await the procs response after submitting the routes so they run at the same time
                val routes = routesDefs.awaitAll()

                appsv3.map { appRes ->
                    val scrapable = isApplicationInScrapableState(appRes.state.toString())
                    val annotationScrape = appRes.metadata.annotations.getOrDefault(PROMETHEUS_IO_SCRAPE, "false") == "true" //TODO Add annotation scrape support back in
                    val annotationMetricsPath = appRes.metadata.annotations[PROMETHEUS_IO_PATH]

                    val proc = procs.firstOrNull { it?.relationships?.app?.data?.id == appRes.id }
                    val route = routes.firstOrNull { it.first == appRes.id }?.second

                    AppResponse(
                            appRes.id,
                            appRes.name,
                            space,
                            scrapable,
                            annotationScrape,
                            annotationMetricsPath,

                            instances = proc?.instances ?: 0,
                            routes = route?.map {
                                DomainResponse(space.org.id,
                                        it.host,
                                        false, //it.relationships.domain.data ?: false, //TODO calculate internal routes
                                        "")
                            } ?: listOf(),
                            urls = route?.map { it.url } ?: listOf()
                    )
                }

            }
        }.awaitAll().flatten()
    }

    private suspend fun List<SpaceResponse>.resolveAppsV2(spaceSummaries: List<SpaceSummaryAppResponse>) = coroutineScope {
        return@coroutineScope this@resolveAppsV2.map { space ->
            async {
                cfAccessor.retrieveAllApplicationIdsInSpace(space.org.cfClient, space.org.id, space.spaceId).map { appRes ->
                    val scrapable = isApplicationInScrapableState(appRes.entity.state)
                    val appNameLower = appRes.entity.name.lowercase(LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON)
                    val summary = spaceSummaries.firstOrNull { it.appName == appNameLower }?.appSummary

                    AppResponse(
                            appRes.metadata.id,
                            appRes.entity.name,
                            space,
                            scrapable,
                            false,
                            null,

                            instances = summary?.instances ?: 0,
                            routes = summary?.routes?.map {
                                DomainResponse(space.org.id,
                                        it.domain.id,
                                        it.domain.internal ?: false,
                                        it.domain.name)
                            } ?: listOf(),
                            urls = summary?.urls ?: listOf())
                }
            }
        }.awaitAll().flatten()
    }

    private fun isApplicationInScrapableState(state: String): Boolean {
        return "STARTED" == state
    }

    companion object {
        const val PROMETHEUS_IO_SCRAPE = "prometheus.io/scrape"
        const val PROMETHEUS_IO_PATH = "prometheus.io/path"
        val LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON: Locale = Locale.ENGLISH
    }
}

