package org.cloudfoundry.promregator.scanner

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.JUnitTestUtils
import org.cloudfoundry.promregator.config.Target
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class CachingTargetResolverTest {
    private lateinit var targetResolver: TargetResolver
    private lateinit var cachingTargetResolver: CachingTargetResolver

    private val target1 = Target(orgName = "unittestorg", spaceName = "unittestspace", applicationName = "testapp", path = "path", protocol = "https")
    private val target2 = Target(orgName = "unittestorg", spaceName = "unittestspace", applicationName = "testapp2", path = "path", protocol = "https")
    private val targetRegex = Target(orgName = "unittestorg", spaceName = "unittestspace", applicationName = "testapp", path = "path", protocol = "https")

    private val rTarget1 = ResolvedTarget (target1)
    private val rTarget2 = ResolvedTarget (target2)

    @BeforeEach
    fun setup() {
        targetResolver = mockk()

        cachingTargetResolver = CachingTargetResolver(targetResolver, 300)
    }

    @Test
    fun `resolveTargets should pass through to parent resolver and be cached`() {
        every { targetResolver.resolveTargets(any())} returns listOf(rTarget1, rTarget2)

        val actualList = cachingTargetResolver.resolveTargets(listOf(target1, target2))

        assertThat(actualList).hasSize(2)

        assertThat(actualList).containsExactly(rTarget1, rTarget2)

        //Call a second time to trigger cache
        cachingTargetResolver.resolveTargets(listOf(target1, target2))

        verify(exactly = 1) { targetResolver.resolveTargets(any())} //Parent resolve should only have been called once
    }


    @Test
    fun `repeated calls only make requests for targets that are missing in the cache `() {
        val list= listOf(target1)

        every { targetResolver.resolveTargets(listOf(target1))} returns listOf(rTarget1)
        every { targetResolver.resolveTargets(listOf(target2))} returns listOf(rTarget2)

        // fill the cache
        val actualList = cachingTargetResolver.resolveTargets(list)
        assertThat(actualList).containsExactly(rTarget1)
        verify(exactly = 1) { targetResolver.resolveTargets(listOf(target1))}
        verify(exactly = 0) { targetResolver.resolveTargets(listOf(target2))} //Target 2 hasn't been called

        cachingTargetResolver.resolveTargets(listOf(target1, target2))
        verify(exactly = 1) { targetResolver.resolveTargets(listOf(target1))} //Taarget 1 is cached and isn't called aa second time
        verify(exactly = 1) { targetResolver.resolveTargets(listOf(target2))}
    }

    @Test
    fun `when duplicate targets are returned we only return the distinct ones`() {
        every { targetResolver.resolveTargets(any())} returns listOf(rTarget1, rTarget1, rTarget2)

        val list = listOf(target1, targetRegex)
        val actualList = cachingTargetResolver.resolveTargets(list)
        assertThat(actualList).hasSize(2)

        assertThat(actualList.filter { it == rTarget1 }).hasSize(1)
        assertThat(actualList.filter { it == rTarget2 }).hasSize(1)
    }

    companion object {
        @AfterAll
        fun cleanupEnvironment() {
            JUnitTestUtils.cleanUpAll()
        }
    }
}