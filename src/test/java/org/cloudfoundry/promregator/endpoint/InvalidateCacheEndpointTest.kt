package org.cloudfoundry.promregator.endpoint

import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.cfaccessor.CFAccessorCache
import org.cloudfoundry.promregator.scanner.CachingTargetResolver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

//@SpringBootTest(classes = MockedAppInstanceScannerEndpointSpringApplication.class)
class InvalidateCacheEndpointTest {
    private lateinit var subject: InvalidateCacheEndpoint
    private val cfAccessorCache = mockk<CFAccessorCache>(relaxed = true)
    private val targetResolver = mockk<CachingTargetResolver>(relaxed = true)

    @BeforeEach
    fun setup() {
        subject = InvalidateCacheEndpoint(cfAccessorCache, targetResolver)
    }

    @Test
    fun testInvalidateCacheAll() {

        val response = subject.invalidateCache(true, true, true, true)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        verify { cfAccessorCache.invalidateCacheApplications() }
        verify { cfAccessorCache.invalidateCacheOrg() }
        verify { cfAccessorCache.invalidateCacheSpace() }

        verify { targetResolver.invalidateCache() }
    }

    @Test
    fun testInvalidateCacheNone() {

        val response = subject.invalidateCache(false, false, false, false)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        verify { cfAccessorCache wasNot called }
        verify { targetResolver wasNot called }
    }

}