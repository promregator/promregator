package org.cloudfoundry.promregator.lite.cfaccessor

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.http.conn.util.InetAddressUtils
import org.cloudfoundry.client.v2.OrderDirection
import org.cloudfoundry.client.v2.info.GetInfoRequest
import org.cloudfoundry.client.v2.spaces.*
import org.cloudfoundry.client.v3.Resource
import org.cloudfoundry.client.v3.applications.GetApplicationProcessRequest
import org.cloudfoundry.client.v3.applications.GetApplicationProcessResponse
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesRequest
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse
import org.cloudfoundry.client.v3.routes.RouteResource
import org.cloudfoundry.promregator.lite.cfaccessor.RequestType.*
import org.cloudfoundry.promregator.lite.config.ApiConfig
import org.cloudfoundry.promregator.lite.config.CloudFoundryConfig
import org.cloudfoundry.promregator.lite.config.ConfigurationException
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration
import org.cloudfoundry.promregator.lite.discovery.RateLimiter
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
import java.util.regex.Pattern
import org.cloudfoundry.client.v2.PaginatedRequest as PaginatedRequestV2
import org.cloudfoundry.client.v2.Resource as ResourceV2
import org.cloudfoundry.client.v2.applications.ApplicationResource as ApplicationResourceV2
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest as ListApplicationsRequestV2
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse as ListApplicationsResponseV2
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest as ListOrganizationsRequestV2
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse as ListOrganizationsResponseV2
import org.cloudfoundry.client.v2.organizations.OrganizationResource as OrganizationResourceV2
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest as ListSpacesRequestV2
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse as ListSpacesResponseV2
import org.cloudfoundry.client.v2.spaces.SpaceResource as SpaceResourceV2
import org.cloudfoundry.client.v3.PaginatedRequest as V3PaginatedRequest
import org.cloudfoundry.client.v3.applications.ApplicationResource as ApplicationResourceV3
import org.cloudfoundry.client.v3.applications.ListApplicationsRequest as ListApplicationsRequestV3
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse as ListApplicationsResponseV3
import org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest as ListOrganizationsRequestV3
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse as ListOrganizationsResponseV3
import org.cloudfoundry.client.v3.organizations.OrganizationResource as OrganizationResourceV3
import org.cloudfoundry.client.v3.spaces.ListSpacesRequest as ListSpacesRequestV3
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse as ListSpacesResponseV3
import org.cloudfoundry.client.v3.spaces.SpaceResource as SpaceResourceV3

private val log = mu.KotlinLogging.logger { }

@Component
class SimpleCFAccessor(
        private val meterRegistry: MeterRegistry,
) {
    private val rateLimiter = RateLimiter(600)
    private var _isV3Enabled = false
    val isV3Enabled: Boolean get() = _isV3Enabled

    init {
        meterRegistry.gauge("promregator.cf.ratelimit.available", rateLimiter) { it.availableTokens.toDouble() }
        meterRegistry.gauge("promregator.cf.ratelimit.queue", rateLimiter) { it.waitQueue.toDouble() }
        meterRegistry.more().counter("promregator.cf.ratelimit.wait_seconds", Tags.empty(), rateLimiter) { (it.totalWaitTimeMs / 1000.0) }
    }

    private suspend fun <T> rateLimitAndRecord(requestType: RequestType, block: suspend () -> T): T? {
        return rateLimiter.whenTokenAvailable {
            val timer = Timer.start()
            val result = try {
                block()
            } catch (e: Exception) {
                log.error(e) { "Error making request type: $requestType" }
                null
            }
            timer.stop(meterRegistry.timer("promregator.cffetch.single", "type", requestType.metricName))
            result
        }
    }



    suspend fun retrieveAllOrgIds(cfClient: ReactorCloudFoundryClient): List<OrganizationResourceV2> {
        return cfQueryV2<OrganizationResourceV2, ListOrganizationsResponseV2, ListOrganizationsRequestV2>(cfClient, ORG) {
            request { pageNumber, order, pageSize ->
                ListOrganizationsRequestV2.builder()
                        .orderDirection(order)
                        .resultsPerPage(pageSize)
                        .page(pageNumber)
                        .build()
            }
            query { organizations().list(it) }
        }
    }

    /* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceIdsInOrg(java.lang.String)
	 */
    suspend fun retrieveSpaceIdsInOrg(cfClient: ReactorCloudFoundryClient, orgId: String): List<SpaceResourceV2> {
        return cfQueryV2<SpaceResourceV2, ListSpacesResponseV2, ListSpacesRequestV2>(cfClient, SPACE_IN_ORG) {
            request { pageNumber, order, pageSize ->
                ListSpacesRequestV2.builder()
                        .organizationId(orgId)
                        .orderDirection(order)
                        .resultsPerPage(pageSize)
                        .page(pageNumber)
                        .build()
            }
            query { spaces().list(it) }
        }
    }

    /* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveAllApplicationIdsInSpace(java.lang.String, java.lang.String)
	 */
    suspend fun retrieveAllApplicationIdsInSpace(cfClient: ReactorCloudFoundryClient, orgId: String, spaceId: String): List<ApplicationResourceV2> {
        return cfQueryV2<ApplicationResourceV2, ListApplicationsResponseV2, ListApplicationsRequestV2>(cfClient, ALL_APPS_IN_SPACE) {
            request { pageNumber, order, pageSize ->
                ListApplicationsRequestV2.builder()
                        .organizationId(orgId)
                        .spaceId(spaceId)
                        .orderDirection(order)
                        .resultsPerPage(pageSize)
                        .page(pageNumber)
                        .build()
            }
            query { applicationsV2().list(it) }
        }
    }

    suspend fun retrieveSpaceSummary(cfClient: ReactorCloudFoundryClient, spaceId: String): GetSpaceSummaryResponse? {
        // Note that GetSpaceSummaryRequest is not paginated
        val request = GetSpaceSummaryRequest.builder().spaceId(spaceId).build()
        return try {
            cfClient.spaces().getSummary(request)?.awaitSingle()
        } catch (e: Exception) {
            log.error(e) { "Error retrieving spaceSummary api:$cfClient spaceId:$spaceId" }
            null
        }
    }

    suspend fun retrieveSpaceSummaryV3(cfClient: ReactorCloudFoundryClient, spaceId: String): GetSpaceSummaryResponse? {
        // Note that GetSpaceSummaryRequest is not paginated
        val request = GetSpaceSummaryRequest.builder().spaceId(spaceId).build()
        return try {
            cfClient.spaces()?.getSummary(request)?.awaitSingle()
        } catch (e: Exception) {
            log.error(e) { "Error retrieving spaceSummary api:$cfClient spaceId:$spaceId" }
            null
        }
    }

    suspend fun retrieveAllDomains(cfClient: ReactorCloudFoundryClient, orgId: String): org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse? {
        val request = org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsRequest.builder().organizationId(orgId).build()
        return cfClient.organizations()?.listDomains(request)?.awaitSingle()
    }

    suspend fun retrieveAllDomainsV3(cfClient: ReactorCloudFoundryClient, orgId: String): org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse? {
        val request = org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsRequest.builder().organizationId(orgId).build()
        return try {
            cfClient.organizationsV3()?.listDomains(request)?.awaitSingle()
        } catch (e: Exception) {
            log.error(e) {"Error listing domains fo api:$cfClient"}
            null
        }
    }

    suspend fun retrieveAllOrgIdsV3(cfClient: ReactorCloudFoundryClient, ): List<OrganizationResourceV3> {
        if (!isV3Enabled) {
            error("V3 API is not supported on your foundation.")
        }

        return cfQueryV3<OrganizationResourceV3, ListOrganizationsResponseV3, ListOrganizationsRequestV3>(cfClient, ORG) {
            request { pageNumber, resultsPerPage ->
                ListOrganizationsRequestV3.builder()
                    .perPage(resultsPerPage)
                    .page(pageNumber)
                    .build()
            }
            query { organizationsV3().list(it) }
        }
    }

    suspend fun retrieveSpaceIdsInOrgV3(cfClient: ReactorCloudFoundryClient, orgId: String): List<SpaceResourceV3> {
        if (!isV3Enabled) {
            error("V3 API is not supported on your foundation.")
        }

        return cfQueryV3<SpaceResourceV3, ListSpacesResponseV3, ListSpacesRequestV3>(cfClient, SPACE_IN_ORG) {
            request { pageNumber, resultsPerPage ->
                ListSpacesRequestV3.builder()
                        .organizationId(orgId)
                        .perPage(resultsPerPage)
                        .page(pageNumber)
                        .build()
            }
            query { spacesV3().list(it) }
        }
    }

    suspend fun retrieveAllApplicationsInSpaceV3(cfClient: ReactorCloudFoundryClient, orgId: String, spaceId: String): List<ApplicationResourceV3> {
        if (!isV3Enabled) {
            error("V3 API is not supported on your foundation.")
        }

        return cfQueryV3<ApplicationResourceV3, ListApplicationsResponseV3, ListApplicationsRequestV3>(cfClient, ALL_APPS_IN_SPACE) {
            request { pageNumber, resultsPerPage ->
                ListApplicationsRequestV3.builder()
                        .organizationId(orgId)
                        .spaceId(spaceId)
                        .perPage(resultsPerPage)
                        .page(pageNumber)
                        .build()
            }
            query { applicationsV3().list(it) }
        }
    }
    suspend fun retrieveApplicationsRoutesV3(cfClient: ReactorCloudFoundryClient, appId: String): List<RouteResource> {
        if (!isV3Enabled) {
            error("V3 API is not supported on your foundation.")
        }

        return cfQueryV3<RouteResource, ListApplicationRoutesResponse, ListApplicationRoutesRequest>(cfClient, OTHER) {
            request { pageNumber, resultsPerPage ->
                ListApplicationRoutesRequest.builder()
                        .applicationId(appId)
                        .perPage(resultsPerPage)
                        .page(pageNumber)
                        .build()
            }

            query { applicationsV3().listRoutes(it) }
        }
    }

    suspend fun retrieveApplicationProcessV3(cfClient: ReactorCloudFoundryClient, appId: String): GetApplicationProcessResponse? {
        if (!isV3Enabled) {
            error("V3 API is not supported on your foundation.")
        }
        val req = GetApplicationProcessRequest.builder()
                .applicationId(appId)
                .type("web").build()

        return cfClient.applicationsV3()?.getProcess(req)?.awaitSingle()
    }

    private suspend fun <R : ResourceV2<*>, T : org.cloudfoundry.client.v2.PaginatedResponse<R>, S : PaginatedRequestV2>
            cfQueryV2(cfClient: ReactorCloudFoundryClient,
                      type: RequestType,
                      pageSize: Int = 100,
                      orderDirection: OrderDirection = OrderDirection.ASCENDING,
                      requestBlock: CfClientDslV2<R, T, S>.() -> Unit): List<R> {
        val dsl = CfClientDslV2<R, T, S>(cfClient, type, pageSize, orderDirection)
        dsl.requestBlock()
        return dsl.runQueries()
    }

    private suspend fun <R : Resource, T : org.cloudfoundry.client.v3.PaginatedResponse<R>, S : V3PaginatedRequest> cfQueryV3(
            cfClient: ReactorCloudFoundryClient, type: RequestType, requestBlock: CfClientDslV3<R, T, S>.() -> Unit): List<R> {
        val dsl = CfClientDslV3<R, T, S>(cfClient, type)
        dsl.requestBlock()
        return dsl.runQueries()
    }

    inner class CfClientDslV2<R : ResourceV2<*>, T : org.cloudfoundry.client.v2.PaginatedResponse<R>, S : PaginatedRequestV2>(
            private val cfClient: ReactorCloudFoundryClient?,
            private val type: RequestType,
            private val pageSize: Int,
            private val orderDirection: OrderDirection,
    ) {
        lateinit var requestTemplate: (Int, OrderDirection, Int) -> S
        lateinit var queryTemplate: ReactorCloudFoundryClient.(S) -> Mono<T>

        fun request(block: (Int, OrderDirection, Int) -> S) {
            requestTemplate = block
        }

        fun query(block: ReactorCloudFoundryClient.(S) -> Mono<T>) {
            queryTemplate = block
        }

        suspend fun runQueries(): List<R> {
            val req = requestTemplate(1, orderDirection, pageSize)

            val clientSafe = cfClient ?: return listOf()
            val first = rateLimitAndRecord(type) { clientSafe.queryTemplate(req).awaitSingle() }
            val totalPages = first?.totalPages ?: 0
            val firstResources = first?.resources ?: listOf()

            val otherPages = if (totalPages > 1) {
                coroutineScope {
                    (1..totalPages).map {
                        val otherReq = requestTemplate(it, orderDirection, pageSize)
                        async { rateLimitAndRecord(type) { clientSafe.queryTemplate(otherReq).awaitSingle() } }
                    }.awaitAll().flatMap { it?.resources ?: listOf() }
                }

            } else {
                listOf()
            }

            return (firstResources + otherPages)
        }
    }


    inner class CfClientDslV3<R : Resource, T : org.cloudfoundry.client.v3.PaginatedResponse<R>, S : V3PaginatedRequest>(
            private val cfClient: ReactorCloudFoundryClient?,
            private val type: RequestType,
            private val resultsPerPage: Int = 100
    ) {
        lateinit var requestTemplate: (Int, Int) -> S
        lateinit var queryTemplate: ReactorCloudFoundryClient.(S) -> Mono<T>

        fun request(block: (Int, Int) -> S) {
            requestTemplate = block
        }

        fun query(block: ReactorCloudFoundryClient.(S) -> Mono<T>) {
            queryTemplate = block
        }

        suspend fun runQueries(): List<R> {
            val req = requestTemplate(1, resultsPerPage)

            val clientSafe = cfClient ?: return listOf()
            val first = rateLimitAndRecord(type) { clientSafe.queryTemplate(req).awaitSingle() }
            val totalPages = first?.pagination?.totalPages ?: 0
            val firstResources = first?.resources ?: listOf()

            val otherPages = if (totalPages > 1) {
                coroutineScope {
                    (1..totalPages).map {
                        val otherReq = requestTemplate(it, resultsPerPage)
                        async { rateLimitAndRecord(type) { clientSafe.queryTemplate(otherReq).awaitSingle() } }
                    }.awaitAll().flatMap { it?.resources ?: listOf() }
                }

            } else {
                listOf()
            }

            return (firstResources + otherPages)
        }
    }


}
