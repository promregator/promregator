package org.cloudfoundry.promregator.endpoint

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.JUnitTestUtils
import org.cloudfoundry.promregator.auth.AuthenticatorController
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.scanner.Instance
import org.cloudfoundry.promregator.scanner.ResolvedTarget
import org.cloudfoundry.promregator.textformat004.Parser
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import java.util.concurrent.Executors
import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest

class SingleTargetMetricsEndpointTest {

    private val currentPromregatorInstanceIdentifier = UUID.randomUUID()
    private val mockedHttpServletRequest = mockk<HttpServletRequest>()
    private lateinit var subject: TestableSingleTargetMetricsEndpoint
    private val instanceCache = mockk<InstanceCache>()

    @BeforeEach
    fun setup() {
        JUnitTestUtils.cleanUpAll()
        clearMocks(mockedHttpServletRequest)

        every { mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER) } returns UUID.randomUUID().toString()

        val promConfig = PromregatorConfiguration()


        subject = TestableSingleTargetMetricsEndpoint(false,
                Executors.newSingleThreadExecutor(),
                AuthenticatorController(promConfig),
                null,
                0,
                5000,
                false,
                5000,
                5000,
                currentPromregatorInstanceIdentifier,
                instanceCache)
    }

    @Test
    fun `get metrics`() {
        val rt = ResolvedTarget().apply {
            applicationId = "faedbb0a-2273-4cb4-a659-bd31331f7daf"
            orgName = "unittestorg"
            spaceName = "unittestspace"
            applicationName = "unittestapp"

        }
        val dummyInstance = Instance(rt, "0", "http://localhost/dummy")

        every { instanceCache.getCachedInstance(dummyInstance.hash)} returns dummyInstance

        val response = subject.getMetrics(dummyInstance.hash, mockedHttpServletRequest).body

        assertThat(response).isNotEmpty()

        val parser = Parser(response)
        val mapMFS = parser.parse()

        assertThat(mapMFS).containsKey("metric_unittestapp")
        assertThat(mapMFS).doesNotContainKey("metric_unittestapp2")
    }

    @Test
    fun testNegativeIsLoopbackScrapingRequest() {
        every { mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER) } returns currentPromregatorInstanceIdentifier.toString()

        val rt = ResolvedTarget()
        rt.applicationId = "faedbb0a-2273-4cb4-a659-bd31331f7daf"
        val dummyInstance = Instance(rt, "0", "http://localhost/dummy")
        assertThrows<LoopbackScrapingDetectedException> {
            subject.getMetrics(dummyInstance.hash, mockedHttpServletRequest)
        }
    }

    @Test
    fun testPositiveIsNotALoopbackScrapingRequest() {
        every { mockedHttpServletRequest.getHeader(EndpointConstants.HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER) } returns UUID.randomUUID().toString()

        val rt = ResolvedTarget()
        rt.applicationId = "faedbb0a-2273-4cb4-a659-bd31331f7daf"
        val dummyInstance = Instance(rt, "0", "http://localhost/dummy")

        every { instanceCache.getCachedInstance(dummyInstance.hash)} returns dummyInstance

        val result = subject.getMetrics(dummyInstance.hash, mockedHttpServletRequest) // real test: no exception is raised
        assertThat(result).isNotNull  // trivial assertion to ensure that unit test is providing an assertion
    }

}