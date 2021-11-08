package org.cloudfoundry.promregator.config.validations

import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration
import org.cloudfoundry.promregator.lite.config.CfTarget
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PreferredRouteRegexMustBeCompilableTest {

    @Test
    fun testValidDoesNotBreak() {
        val subject = PreferredRouteRegexMustBeCompilable()
        val target = CfTarget(preferredRouteRegex = listOf("dummy"))
        val promregatorConfiguration = PromregatorConfiguration(listOf(target))
        Assertions.assertNull(subject.validate(promregatorConfiguration))
    }

    @Test
    fun testInvalidRaisesError() {
        val subject = PreferredRouteRegexMustBeCompilable()
        val target = CfTarget(preferredRouteRegex = listOf("["))
        val promregatorConfiguration = PromregatorConfiguration(listOf(target))
        Assertions.assertNotNull(subject.validate(promregatorConfiguration))
    }
}
