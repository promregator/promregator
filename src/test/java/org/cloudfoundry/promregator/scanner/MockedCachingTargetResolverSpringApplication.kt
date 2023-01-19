package org.cloudfoundry.promregator.scanner

import java.util.LinkedList
import org.cloudfoundry.promregator.cfaccessor.CFAccessor
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock
import org.cloudfoundry.promregator.lite.config.CfTarget
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
            val target1 = CfTarget(
                    orgName = "unittestorg",
                    spaceName = "unittestspace",
                    applicationName = "testapp",
                    path = "path",
                    protocol = "https"
            )
            val target2 = CfTarget(
                    orgName = "unittestorg",
                    spaceName = "unittestspace",
                    applicationName = "testapp2",
                    path = "path",
                    protocol = "https"
            )
            val targetAllInSpace = CfTarget(
                    orgName = "unittestorg",
                    spaceName = "unittestspace",
                    // application Name is missing
                    path = "path",
                    protocol = "https"
            )
            val targetRegex = CfTarget()
            var rTarget1: ResolvedTarget = ResolvedTarget(target1)
            var rTarget2: ResolvedTarget = ResolvedTarget(target2)
        }


        override fun resolveTargets(configTarget: List<CfTarget>): List<ResolvedTarget> {
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
