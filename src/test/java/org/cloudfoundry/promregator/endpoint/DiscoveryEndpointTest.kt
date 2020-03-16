package org.cloudfoundry.promregator.endpoint

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.config.DiscoveryConfig
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.Target
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer
import org.cloudfoundry.promregator.scanner.Instance
import org.cloudfoundry.promregator.scanner.ResolvedTarget
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.servlet.http.HttpServletRequest

class DiscoveryEndpointTest {
    private lateinit var subject: DiscoveryEndpoint
    private lateinit var cfDiscoverer: CFMultiDiscoverer

    @BeforeEach
    fun setup() {
        cfDiscoverer = mockk()
        subject = DiscoveryEndpoint(cfDiscoverer, PromregatorConfiguration(
                discovery = DiscoveryConfig(
                        hostname = "discovery-hostname",
                        port = 1234,
                        ownMetricsEndpoint = true)))
    }


    @Test
    fun `discovery endpoint should not include promregator self discovery when ownMetricsEndpoint is false`() {
        val target1 = ResolvedTarget(Target(applicationName = "unittestapp", orgName = "unittestorg", spaceName = "unittestspace"))
        val target2 = ResolvedTarget(Target(applicationName = "unittestapp2", orgName = "unittestorg", spaceName = "unittestspace"))

        every { cfDiscoverer.discover(null, null)} returns listOf(
                Instance(target1, "faedbb0a-2273-4cb4-a659-bd31331f7daf:0", "/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/0"),
                Instance(target1, "faedbb0a-2273-4cb4-a659-bd31331f7daf:1", "/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/1"),
                Instance(target2, "1142a717-e27d-4028-89d8-b42a0c973300:0", "/singleTargetMetrics/1142a717-e27d-4028-89d8-b42a0c973300/0")
        )

        val requestMock = mockk<HttpServletRequest>()
        val responseEntity = subject.getDiscovery(requestMock)
        val response = responseEntity.body ?: Assertions.fail("Responsse is missing")

        assertThat(response).hasSize(4)
        assertThat(response[0].targets).containsExactly("discovery-hostname:1234")
        assertThat(response[1].targets).containsExactly("discovery-hostname:1234")
        assertThat(response[2].targets).containsExactly("discovery-hostname:1234")
        assertThat(response[3].targets).containsExactly("discovery-hostname:1234")

        with(response[0].labels) {
            assertThat(applicationId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf")
            assertThat(applicationName).isEqualTo("unittestapp")
            assertThat(instanceId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf:0")
            assertThat(instanceNumber).isEqualTo("0")
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(targetPath).isEqualTo("/singleTargetMetrics/4229b9c0d0ee3e3617ca9870a4397e9346110935")
        }

        with(response[1].labels) {
            assertThat(applicationId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf")
            assertThat(applicationName).isEqualTo("unittestapp")
            assertThat(instanceId).isEqualTo("faedbb0a-2273-4cb4-a659-bd31331f7daf:1")
            assertThat(instanceNumber).isEqualTo("1")
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(targetPath).isEqualTo("/singleTargetMetrics/f196da8424012697b17ca8f3e28aaa8524ab5c7a")
        }

        with(response[2].labels) {
            assertThat(applicationId).isEqualTo("1142a717-e27d-4028-89d8-b42a0c973300")
            assertThat(applicationName).isEqualTo("unittestapp2")
            assertThat(instanceId).isEqualTo("1142a717-e27d-4028-89d8-b42a0c973300:0")
            assertThat(instanceNumber).isEqualTo("0")
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(targetPath).isEqualTo("/singleTargetMetrics/6f134949dc43fbe0cadd289df8a5f766bc239b16")
        }

        with(response[3].labels) {
            assertThat(applicationId).isNull()
            assertThat(applicationName).isNull()
            assertThat(instanceId).isNull()
            assertThat(instanceNumber).isNull()
            assertThat(orgName).isNull()
            assertThat(spaceName).isNull()
            assertThat(targetPath).isEqualTo("/promregatorMetrics")
        }
    }
}