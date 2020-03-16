package org.cloudfoundry.promregator.discovery

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import mu.KotlinLogging
import org.cloudfoundry.promregator.config.ApiConfig
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.ALL
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.DEFAULT_ID
import org.cloudfoundry.promregator.config.Target
import org.cloudfoundry.promregator.endpoint.InstanceCache
import org.cloudfoundry.promregator.scanner.Instance
import org.cloudfoundry.promregator.scanner.AppInstanceScanner
import org.cloudfoundry.promregator.scanner.ReactiveTargetResolver
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}
@Service
class CachedDiscoverer(
        private val targetResolver: ReactiveTargetResolver,
        private val appInstanceScanner: AppInstanceScanner,
        private val promregatorConfiguration: PromregatorConfiguration,
        meterRegistry: MeterRegistry,
        instanceCache: InstanceCache,
        cf: CloudFoundryConfiguration
        ) {
    private val discoveryCache: LoadingCache<DiscoveryKey, Mono<Map<String,Instance>>> = CaffeineCacheMetrics.monitor(meterRegistry, Caffeine.newBuilder()
            .expireAfterWrite(promregatorConfiguration.discovery.cache.duration)
            .recordStats()
            .build { key: DiscoveryKey ->
                val apiTargets = key.targets

                val targets = if(apiTargets.isNotEmpty()) {
                    logger.debug { "We have ${apiTargets.size} targets configured for api ${key.api}" }
                    apiTargets
                } else {
                    logger.debug { "No targets configured, returning all applications" }
                    if(cf.api.isEmpty()) {
                        listOf(Target(api = key.api, orgRegex = ".+", applicationRegex = ".+"))
                    } else {
                        cf.api.map { Target(api = it.key, orgRegex = ".+", applicationRegex = ".+") }
                    }
                }

                targetResolver.resolveTargets(targets)
                        .doOnNext { logger.debug { "Raw list contains ${it.size} resolved targets" } }
                        .flatMap { resolvedTargets -> appInstanceScanner.determineInstancesFromTargets(resolvedTargets, null, null) }
                        .doOnNext { logger.debug {"Raw list contains ${it.size} instances"} }
                        .map { instances ->
                            instanceCache.loadCache(instances)
                            instances.map { it.hash to it }.toMap()
                }.cache()
            }, "discoveryCache")

    /**
     * performs the discovery based on the configured set of targets in the configuration, (pre-)filtering the returned set applying the filter criteria supplied.
     * The instances discovered are automatically registered at this Discoverer
     * @param applicationIdFilter the (pre-)filter based on ApplicationIds, allowing to early filter the list of instances to discover
     * @param instanceFilter the (pre-)filter based on the Instance instance, allowing to filter the lost if instances to discover
     * @return the list of Instances which were discovered (and registered).
     */
    fun discover(api: String, targets: List<Target>): Mono<Map<String,Instance>> {
        val configTargets = promregatorConfiguration.targets.filter { api == ALL || it.api == api }
        return discoveryCache[DiscoveryKey(api, targets+configTargets)] ?: Mono.error { RuntimeException("Error loading cache") }
    }


    data class DiscoveryKey(
            val api: String,
            val targets: List<Target>
    )



}
