package org.cloudfoundry.promregator.endpoint

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ScrapingFilter : OncePerRequestFilter(), MeterFilter {
    private val additionalTags = ThreadLocal<Tags>()

    override fun doFilterInternal(req: HttpServletRequest, resp: HttpServletResponse, chain: FilterChain) {
        if(req.requestURI.startsWith("/singleTargetMetrics")){
            val (root, path, app) = req.requestURI.split("/")
            additionalTags.set(Tags.of("app", app))
        } else {
            additionalTags.set(Tags.of("app", "none"))
        }
        chain.doFilter(req, resp)
        additionalTags.remove()
    }

    override fun map(id: Meter.Id): Meter.Id {
        return if (id.name == "http.server.requests") {
            val moreTags = additionalTags.get()
            id.withTags(moreTags)
        } else id
    }
}