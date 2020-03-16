package org.cloudfoundry.promregator.endpoint

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.cloudfoundry.promregator.config.DiscoveryConfig
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.Target
import org.cloudfoundry.promregator.discovery.CFDiscoverer
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryLabel
import org.cloudfoundry.promregator.scanner.Instance
import org.cloudfoundry.promregator.scanner.ResolvedTarget
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.servlet.http.HttpServletRequest

class DiscoveryEndpointNoPromregatorMetricsTest {
    private lateinit var subject: DiscoveryEndpoint
    private lateinit var cfDiscoverer: CFMultiDiscoverer

    @BeforeEach
    fun setup() {
        cfDiscoverer = mockk()
        subject = DiscoveryEndpoint(cfDiscoverer, PromregatorConfiguration(
                discovery = DiscoveryConfig(
                        hostname = "discovery-hostname",
                        port = 1234,
                        ownMetricsEndpoint = false)))
    }


    @Test
    fun `discovery endpoint should not include promregator self discovery when ownMetricsEndpoint is false`() {
        val target1 = ResolvedTarget(Target(applicationName = "unittestapp", orgName = "unittestorg", spaceName = "unittestspace"))
        val target2 = ResolvedTarget(Target(applicationName = "unittestapp2", orgName = "unittestorg", spaceName = "unittestspace"))

        every { cfDiscoverer.discover(null, null)} returns listOf(
                Instance(target1, "faedbb0a-2273-4cb4-a659-bd31331f7daf:0", "/app1"),
                Instance(target1, "faedbb0a-2273-4cb4-a659-bd31331f7daf:1", "/app1"),
                Instance(target2, "1142a717-e27d-4028-89d8-b42a0c973300:0", "/app2")
        )

        val requestMock = mockk<HttpServletRequest>()
        val responseEntity = subject.getDiscovery(requestMock)
        val response = responseEntity.body ?: fail("Response is missing")

        assertThat(response).hasSize(3)
        assertThat(response[0].targets).containsExactly("discovery-hostname:1234")
        assertThat(response[1].targets).containsExactly("discovery-hostname:1234")
        assertThat(response[2].targets).containsExactly("discovery-hostname:1234")

        var label: DiscoveryLabel? = null
        with(response[0].labels) {
            assertThat(applicationId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf")
            assertThat(applicationName).isEqualTo("unittestapp")
            assertThat(instanceId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf:0")
            assertThat(instanceNumber).isEqualTo("0")
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(targetPath).isEqualTo("/singleTargetMetrics/4a624cb57f79dfb764cb6f149c7f9feb415c5d84")
        }

        with(response[1].labels) {
            assertThat(applicationId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf")
            assertThat(applicationName).isEqualTo("unittestapp")
            assertThat(instanceId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf:1")
            assertThat(instanceNumber).isEqualTo("1")
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(targetPath).isEqualTo("/singleTargetMetrics/30ee5a88a137a12295a7102535e4b368db53a173")
        }

        with(response[2].labels) {
            assertThat(applicationId).isEqualTo("1142a717-e27d-4028-89d8-b42a0c973300")
            assertThat(applicationName).isEqualTo("unittestapp2")
            assertThat(instanceId).isEqualTo("1142a717-e27d-4028-89d8-b42a0c973300:0")
            assertThat(instanceNumber).isEqualTo("0")
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(targetPath).isEqualTo("/singleTargetMetrics/72468cc7307539375894d60a9328ff3e08d2b6f9")
        }

        // NB: /promregatorMetrics endpoint must not be mentioned here (that's checked with Assert.assertEquals(3, response.length); )
    }

}