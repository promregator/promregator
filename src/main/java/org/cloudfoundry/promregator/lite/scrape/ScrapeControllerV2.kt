package org.cloudfoundry.promregator.lite.scrape

import org.cloudfoundry.promregator.lite.auth.AuthenticatorService
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.runBlocking
import org.cloudfoundry.promregator.lite.discovery.DiscoveryService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.*
import reactor.netty.http.client.HttpClient
import javax.servlet.http.HttpServletRequest

private val log = mu.KotlinLogging.logger { }

@RestController
class ScrapeControllerV2(
        private val discoverer: DiscoveryService,
        private val webClientBuilder: WebClient.Builder,
        private val authService: AuthenticatorService,
) {
    private val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000) // Connection Timeout
            .doOnConnected { connection ->
                connection.addHandlerLast(ReadTimeoutHandler(20)) // Read Timeout
                        .addHandlerLast(WriteTimeoutHandler(20)) // Write Timeout

            }
            .followRedirect(false)
    private val webClient: WebClient = webClientBuilder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()

    @GetMapping(value = ["/v2/singleTargetMetrics/{appId}/{instanceNumber}"])
    fun scrape(@PathVariable appId: String,
               @PathVariable instanceNumber: String,
               httpRequest: HttpServletRequest): ResponseEntity<String> = runBlocking {
        val instance = discoverer.discoverCached()
                .firstOrNull { it.applicationId == appId }
                ?: throw RuntimeException("No application with id $appId found")

        val authEnricher = authService.getAuthenticationEnricherById(instance.target.originalTarget?.authenticatorId)
        log.debug { "Scraping $instance" }

        val respHeaders = HttpHeaders().apply {
            set(HttpHeaders.CONTENT_TYPE, TextFormat.CONTENT_TYPE_004)
            set("X-Promregator-Target-Url", instance.accessUrl)
            set("X-Promregator-Auth-Id", authEnricher.toString())
            set("X-Promregator-Internal", instance.isInternal.toString())
        }
        webClient
                .get()
                .uri(instance.accessUrl)
                .headers {
                    it.set("X-CF-APP-INSTANCE", "${instance.applicationId}:${instance.instanceNumber}")
                    authEnricher.enrichWithAuthentication(it)
                }
                .awaitExchange { resp ->
                    if (resp.statusCode() != HttpStatus.OK) {
                        log.error { "Error scraping ${instance.accessUrl} status:${resp.statusCode()}" }
                        ResponseEntity<String>("# Error scraping ${instance.accessUrl} status:${resp.statusCode()}", respHeaders, resp.statusCode())
                    } else {
                        ResponseEntity<String>(resp.awaitBody(), respHeaders, resp.statusCode())
                    }
                }
    }
}
