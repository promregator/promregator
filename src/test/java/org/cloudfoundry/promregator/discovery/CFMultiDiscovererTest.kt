package org.cloudfoundry.promregator.discovery

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.Target
import org.cloudfoundry.promregator.messagebus.MessageBusDestination
import org.cloudfoundry.promregator.scanner.AppInstanceScanner
import org.cloudfoundry.promregator.scanner.Instance
import org.cloudfoundry.promregator.scanner.ResolvedTarget
import org.cloudfoundry.promregator.scanner.TargetResolver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.jms.core.JmsTemplate
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

class CFMultiDiscovererTest {

    private lateinit var appInstanceScanner: AppInstanceScanner
    private lateinit var cfDiscoverer: CFMultiDiscoverer
    private lateinit var  targetResolver: TargetResolver
    private lateinit var  clock: Clock
    private val promConfig = PromregatorConfiguration()
    private lateinit var jmsTemplate : JmsTemplate

    @BeforeEach
    fun setup() {
        targetResolver = mockk()
        appInstanceScanner = mockk()
        jmsTemplate = mockk()
        clock = mockk()
        every { clock.instant() } returns Instant.parse("2007-12-03T10:15:30.00Z")

        cfDiscoverer = CFMultiDiscoverer(targetResolver, appInstanceScanner, promConfig, jmsTemplate, clock)
    }

    @Test
    fun `discovery should register instaces and cleanup should remove them`() {
        val resolvedTarget = ResolvedTarget().apply {
            orgName = "unittestorg"
            spaceName = "unittestspace"
            applicationName = "testapp"
            applicationId = CFAccessorMock.UNITTEST_APP1_UUID
            protocol = "https"
            path = "/metrics"
            originalTarget = Target()
        }
        val resolvedTargets = listOf(resolvedTarget)

        every { jmsTemplate.convertAndSend(MessageBusDestination.DISCOVERER_INSTANCE_REMOVED, any<String>()) } just runs
        every { targetResolver.resolveTargets(any()) } returns resolvedTargets.toMono()

        every {appInstanceScanner.determineInstancesFromTargets(any(), null, null)} returns listOf(
                Instance(resolvedTarget, CFAccessorMock.UNITTEST_APP1_UUID +":0", "http://url123"),
                Instance(resolvedTarget, CFAccessorMock.UNITTEST_APP1_UUID +":1", "http://url123")
        ).toMono()

        val result = cfDiscoverer.discover(null, null)

        assertThat(result).hasSize(2)


        val i1 = result.find { it.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0"} ?: fail("Instance 0 wasn't found")
        val i2 = result.find { it.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1"} ?: fail("Instance 1 wasn't found")

        assertThat(cfDiscoverer.isInstanceRegistered(i1)).isTrue()
        assertThat(cfDiscoverer.isInstanceRegistered(i2)).isTrue()

        verify(exactly = 0) { jmsTemplate.convertAndSend(any<String>(), any<String>())}

        // early cleaning does not change anything
        cfDiscoverer.cleanup()
        assertThat(cfDiscoverer.isInstanceRegistered(i1)).isTrue()
        assertThat(cfDiscoverer.isInstanceRegistered(i2)).isTrue()
        verify(exactly = 0) { jmsTemplate.convertAndSend(any<String>(), any<String>())}

        // later cleaning does...
        every { clock.instant() } returns Instant.parse("2007-12-03T10:15:30.00Z").plus(10, ChronoUnit.MINUTES)

        cfDiscoverer.cleanup()
        assertThat(cfDiscoverer.isInstanceRegistered(i1)).isFalse()
        assertThat(cfDiscoverer.isInstanceRegistered(i2)).isFalse()
        verify(exactly = 2) { jmsTemplate.convertAndSend(any<String>(), any<String>())}
    }

}