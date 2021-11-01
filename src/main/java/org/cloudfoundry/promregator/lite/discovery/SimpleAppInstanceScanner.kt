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

        val instances = targets.flatMap { target ->
            val accessURL = determineApplicationRoute(target.urls, target.originalTarget?.preferredRouteRegexPattern)
            val domainId = target.routes.firstOrNull { rt ->
                false //accessURL?.startsWith(rt.host + "." + rt.name) ?: false
            }
            val isInternal = false // TODO add this back target. domainId?.domain?.internal ?: false

            val numberOfInstances = target.instances
            val internalRoutePort = target.originalTarget?.internalRoutePort ?: defaultInternalRoutePort

            (0 until numberOfInstances).map { i ->
                val accessUrl = if (isInternal) {
                    formatInternalAccessURL(accessURL, target.metricsPath, internalRoutePort, i)
                } else {
                    formatAccessURL(target.protocol, accessURL, target.metricsPath)
                }
                Instance(target, "${target.appId}:$i", accessUrl, isInternal)
            }
        }.filter { applicationIdFilter(it.applicationId) }
         .filter { instanceFilter(it) }

        return instances
    }

    private fun formatAccessURL(protocol: String, hostnameDomain: String?, path: String): String {
        val applicationUrl = String.format("%s://%s", protocol, hostnameDomain)
        log.debug(String.format("Using Application URL: '%s'", applicationUrl))
        var applUrl = applicationUrl
        if (!applicationUrl.endsWith("/")) {
            applUrl += '/'
        }
        var internalPath = path
        while (internalPath.startsWith("/")) {
            internalPath = internalPath.substring(1)
        }
        return applUrl + internalPath
    }

    private fun formatInternalAccessURL(hostnameDomain: String?, path: String, internalRoutePort: Int,
                                        instanceId: Int): String {
        var port = internalRoutePort
        if (port == 0) {
            port = defaultInternalRoutePort
        }
        val internalURL = String.format("%s.%s:%s", instanceId, hostnameDomain, port)
        log.debug(String.format("Using internal Application URL: '%s'", internalURL))
        return formatAccessURL("http", internalURL, path)
    }

    private fun determineApplicationRoute(urls: List<String>?, patterns: List<Pattern>?): String? {
        if (urls == null || urls.isEmpty()) {
            log.debug("No URLs provided to determine ApplicationURL with")
            return null
        }
        if (patterns == null || patterns.isEmpty()) {
            log.debug("No Preferred Route URL (Regex) provided; taking first Application Route in the list provided")
            return urls[0]
        }
        for (pattern in patterns) {
            for (url in urls) {
                log.debug {"Attempting to match Application Route '$url' against pattern '$pattern'" }
                val m = pattern.matcher(url)
                if (m.matches()) {
                    log.debug { "Match found, using Application Route '$url'" }
                    return url
                }
            }
        }

        // if we reach this here, then we did not find any match in the regex.
        // The fallback then is the old behavior by returned just the first-guess element
        log.debug { "Though Preferred Router URLs were provided, no route matched; taking the first" +
                " route as fallback (compatibility!), which is '${urls.first()}'" }
        return urls.first()
    }
}
