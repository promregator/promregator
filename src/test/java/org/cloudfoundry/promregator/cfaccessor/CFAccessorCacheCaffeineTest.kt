package org.cloudfoundry.promregator.cfaccessor

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration.Companion.DEFAULT_API
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.kotlin.core.publisher.toMono

class CFAccessorCacheCaffeineTest {
    private lateinit var parentMock: CFAccessor
    private lateinit var subject: CFAccessorCacheCaffeine
    
    @BeforeEach
    fun setup() {
        parentMock = mockk()
        subject = CFAccessorCacheCaffeine(InternalMetrics(), parentMock, CloudFoundryConfiguration())
    }

    @Test
    fun `testRetrieveOrgId caches the result`() {
        every { parentMock.retrieveOrgId(DEFAULT_API, "dummy") } returns ListOrganizationsResponse.builder().build().toMono()

        val response1 = subject.retrieveOrgId(DEFAULT_API, "dummy")
        verify(exactly = 1) { parentMock.retrieveOrgId(DEFAULT_API, "dummy") }
        Thread.sleep(10)

        val response2 = subject.retrieveOrgId(DEFAULT_API, "dummy")
        assertThat(response1.block()).isEqualTo(response2.block())

        verify(exactly = 1) { parentMock.retrieveOrgId(DEFAULT_API, "dummy") }
    }


    @Test
    fun `testRetrieveOrgId fetches a fresh result after cache is invalidated`() {
        every { parentMock.retrieveOrgId(DEFAULT_API, "dummy") } returns ListOrganizationsResponse.builder().build().toMono()

        subject.retrieveOrgId(DEFAULT_API, "dummy")
        verify(exactly = 1) { parentMock.retrieveOrgId(DEFAULT_API, "dummy") }
        subject.invalidateCacheOrg()
        subject.retrieveOrgId(DEFAULT_API, "dummy")

        verify(exactly = 2) { parentMock.retrieveOrgId(DEFAULT_API, "dummy") }
    }

    @Test
    fun `testRetrieveSpaceId caches the result`() {
        every { parentMock.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2") } returns ListSpacesResponse.builder().build().toMono()

        val response1 = subject.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2")
        verify(exactly = 1) { parentMock.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2") }

        val response2 = subject.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2")
        assertThat(response1.block()).isEqualTo(response2.block())

        verify(exactly = 1) { parentMock.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2") }
    }

    @Test
    fun `testRetrieveSpaceId fetches a fresh result after cache is invalidated`() {
        every { parentMock.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2") } returns ListSpacesResponse.builder().build().toMono()

        subject.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2")
        verify(exactly = 1) { parentMock.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2") }
        subject.invalidateCacheSpace()
        subject.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2")

        verify(exactly = 2) { parentMock.retrieveSpaceId(DEFAULT_API, "dummy1", "dummy2") }
    }

    @Test
    fun `testRetrieveAllApplicationIdsInSpace caches the result`() {
        every { parentMock.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2") } returns ListApplicationsResponse.builder().build().toMono()

        subject.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2")
        verify(exactly = 1) { parentMock.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2") }

        subject.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2")
        verify(exactly = 1) { parentMock.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2") }
    }

    @Test
    fun `testRetrieveAllApplicationIdsInSpace fetches a fresh result after cache is invalidated`() {
        every { parentMock.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2") } returns ListApplicationsResponse.builder().build().toMono()

        subject.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2")
        verify(exactly = 1) { parentMock.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2") }
        subject.invalidateCacheApplications()
        subject.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2")

        verify(exactly = 2) { parentMock.retrieveAllApplicationIdsInSpace(DEFAULT_API, "dummy1", "dummy2") }
    }

    @Test
    fun `testRetrieveSpaceSummary caches the result`() {
        every { parentMock.retrieveSpaceSummary(DEFAULT_API, "dummy") } returns GetSpaceSummaryResponse.builder().build().toMono()

        val response1 = subject.retrieveSpaceSummary(DEFAULT_API, "dummy")
        verify(exactly = 1) { parentMock.retrieveSpaceSummary(DEFAULT_API, "dummy") }

        val response2 = subject.retrieveSpaceSummary(DEFAULT_API, "dummy")
        assertThat(response1.block()).isEqualTo(response2.block())

        verify(exactly = 1) { parentMock.retrieveSpaceSummary(DEFAULT_API, "dummy") }
    }
}