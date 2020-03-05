package org.cloudfoundry.promregator.endpoint

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.Target
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer
import org.cloudfoundry.promregator.scanner.Instance
import org.cloudfoundry.promregator.scanner.ResolvedTarget
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ExtendWith(SpringExtension::class)
@WebMvcTest(DiscoveryEndpoint::class, properties = ["promregator.simulation.enabled=true", "promregator.lifecycle.enabled=false"])
class DiscoveryEndpointControllerTest(
       @Autowired private val mockMvc: MockMvc
) {
    @MockkBean private lateinit var discoverer: CFMultiDiscoverer

    /** Test for [DiscoveryEndpoint.getDiscovery] */
    @Test
    fun `get returns 503 when no targets are resolvable`() {
        every {discoverer.discover(any(), any()) } returns listOf()

        mockMvc.get("/discovery").andDo { print() }.andExpect {
            status { isServiceUnavailable }
        }
    }

    /** Test for [DiscoveryEndpoint.getDiscovery] */
    @Test
    fun `get returns json that includes the apps space, instance, and metrics path`() {
        every {discoverer.discover(any(), any()) } returns listOf(Instance(ResolvedTarget().apply {
            this.applicationName = "testApp"
            this.orgName = "testOrg"
            this.path = "/a/path"
            this.protocol = "http"
            this.spaceName = "testSpace"
            this.originalTarget = Target()
        }, "appguid:0", "/url1"))

        mockMvc.get("/discovery").andDo { print() }.andExpect {
            status { isOk }
            jsonPath("$[0].labels.__meta_promregator_target_path", `is`("/singleTargetMetrics/appguid/0"))
            jsonPath("$[0].labels.__meta_promregator_target_orgName", `is`("testOrg"))
            jsonPath("$[0].labels.__meta_promregator_target_spaceName", `is`("testSpace"))
            jsonPath("$[0].labels.__meta_promregator_target_applicationName", `is`("testApp"))
            jsonPath("$[0].labels.__meta_promregator_target_applicationId", `is`("appguid"))
            jsonPath("$[0].labels.__meta_promregator_target_instanceNumber", `is`("0"))
            jsonPath("$[0].labels.__meta_promregator_target_instanceId", `is`("appguid:0"))
            jsonPath("$[0].labels.__metrics_path__", `is`("/singleTargetMetrics/appguid/0"))
        }
    }
}