package org.cloudfoundry.promregator.cfaccessor

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration
import org.cloudfoundry.promregator.config.PromregatorConfiguration.Companion.DEFAULT_ID
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

class CFAccessorCacheCaffeineTimeoutTest {
    private lateinit var parentMock: CFAccessor
    private lateinit var subject: CFAccessorCacheCaffeine

    @BeforeEach
    fun ssetup() {
        parentMock = mockk()
        subject = CFAccessorCacheCaffeine(InternalMetrics(), parentMock, CloudFoundryConfiguration())
    }

    @BeforeEach
    fun setupMocks() {
        every {parentMock.retrieveOrgId(any(),"dummy") } answers { Mono.error(TimeoutException("Unit test timeout raised")) }
        every {parentMock.retrieveSpaceId(any(),"dummy1", "dummy2") } answers { Mono.error(TimeoutException("Unit test timeout raised")) }
        every {parentMock.retrieveAllApplicationIdsInSpace(any(),"dummy1", "dummy2") } answers { Mono.error(TimeoutException("Unit test timeout raised")) }
        every {parentMock.retrieveSpaceSummary(any(),"dummy") } answers { Mono.error(TimeoutException("Unit test timeout raised")) }
    }

    @Test
    fun testRetrieveOrgId() {
        val response1 = subject.retrieveOrgId(DEFAULT_ID,"dummy")
        response1.subscribe()

        verify(exactly = 1) { parentMock.retrieveOrgId(any(),"dummy") }

        // required to permit asynchronous updates of caches => test stability
        Thread.sleep(10)

        val response2 = subject.retrieveOrgId(DEFAULT_ID,"dummy")
        response2.subscribe()
        assertThat(response1).isNotEqualTo(response2)

        verify(exactly = 2) { parentMock.retrieveOrgId(any(),"dummy") }
    }

    @Test
    fun testRetrieveSpaceId() {
        val response1 = subject.retrieveSpaceId(DEFAULT_ID,"dummy1", "dummy2")
        response1.subscribe()

        verify(exactly = 1) { parentMock.retrieveSpaceId(any(),"dummy1", "dummy2") }
        // required to permit asynchronous updates of caches => test stability
        Thread.sleep(10)
        val response2 = subject.retrieveSpaceId(DEFAULT_ID,"dummy1", "dummy2")
        response2.subscribe()
        assertThat(response1).isNotEqualTo(response2)

        verify(exactly = 2) { parentMock.retrieveSpaceId(any(),"dummy1", "dummy2") }
    }

    @Test
    fun testRetrieveAllApplicationIdsInSpace() {
        val response1 = subject.retrieveAllApplicationIdsInSpace(DEFAULT_ID,"dummy1", "dummy2")
        response1.subscribe()

        verify(exactly = 1) { parentMock.retrieveAllApplicationIdsInSpace(any(),"dummy1", "dummy2") }
        // required to permit asynchronous updates of caches => test stability
        Thread.sleep(10)
        val response2 = subject.retrieveAllApplicationIdsInSpace(DEFAULT_ID,"dummy1", "dummy2")
        response2.subscribe()
        assertThat(response1).isNotEqualTo(response2)
        verify(exactly = 2) { parentMock.retrieveAllApplicationIdsInSpace(any(),"dummy1", "dummy2") }
    }

    @Test
    fun testRetrieveSpaceSummary() {
        val response1 = subject.retrieveSpaceSummary(DEFAULT_ID,"dummy")
        response1.subscribe()

        verify(exactly = 1) { parentMock.retrieveSpaceSummary(any(),"dummy") }
        // required to permit asynchronous updates of caches => test stability
        Thread.sleep(10)
        val response2 = subject.retrieveSpaceSummary(DEFAULT_ID,"dummy")
        response2.subscribe()
        assertThat(response1).isNotEqualTo(response2)
        verify(exactly = 2) { parentMock.retrieveSpaceSummary(any(),"dummy") }
    }

}