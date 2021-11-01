package org.cloudfoundry.promregator.scanner

import org.cloudfoundry.promregator.scanner.TargetResolver
import org.cloudfoundry.promregator.scanner.ResolvedTarget
import org.cloudfoundry.promregator.scanner.MockedCachingTargetResolverSpringApplication.MockedTargetResolver
import java.util.LinkedList
import org.cloudfoundry.promregator.cfaccessor.CFAccessor
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock
import org.cloudfoundry.promregator.lite.config.Target
import org.cloudfoundry.promregator.scanner.CachingTargetResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockedCachingTargetResolverSpringApplication {
    class MockedTargetResolver : TargetResolver {
        var isRequestForTarget1 = false
            private set
        var isRequestForTarget2 = false
            private set
        var isRequestForTargetAllInSpace = false
            private set
        var isRequestForTargetWithRegex = false
            private set


        companion object {
            val target1 = Target(
                    orgName = "unittestorg",
                    spaceName = "unittestspace",
                    applicationName = "testapp",
                    path = "path",
                    protocol = "https"
            )
            val target2 = Target(
                    orgName = "unittestorg",
                    spaceName = "unittestspace",
                    applicationName = "testapp2",
                    path = "path",
                    protocol = "https"
            )
            val targetAllInSpace = Target(
                    orgName = "unittestorg",
                    spaceName = "unittestspace",
                    // application Name is missing
                    path = "path",
                    protocol = "https"
            )
            val targetRegex = Target()
            var rTarget1: ResolvedTarget = ResolvedTarget(target1)
            var rTarget2: ResolvedTarget = ResolvedTarget(target2)
        }


        override fun resolveTargets(configTarget: List<Target>): List<ResolvedTarget> {
            val response: MutableList<ResolvedTarget> = LinkedList()
            for (t in configTarget) {
                if (t === target1) {
                    isRequestForTarget1 = true
                    response.add(rTarget1)
                } else if (t === target2) {
                    isRequestForTarget2 = true
                    response.add(rTarget2)
                } else if (t === targetAllInSpace) {
                    isRequestForTargetAllInSpace = true
                    response.add(rTarget1)
                    response.add(rTarget2)
                } else if (t === targetRegex) {
                    isRequestForTargetWithRegex = true
                    response.add(rTarget1)
                    response.add(rTarget2)
                }
            }
            return response
        }

        fun resetRequestFlags() {
            isRequestForTarget1 = false
            isRequestForTarget2 = false
            isRequestForTargetAllInSpace = false
            isRequestForTargetWithRegex = false
        }
    }

    @Bean
    fun cfAccessor(): CFAccessor {
        return CFAccessorMock()
    }

    @Bean
    fun targetResolver(): TargetResolver {
        return MockedTargetResolver()
    }

    @Bean
    fun cachingTargetResolver(targetResolver: TargetResolver?): CachingTargetResolver {
        return CachingTargetResolver(targetResolver)
    }
}
