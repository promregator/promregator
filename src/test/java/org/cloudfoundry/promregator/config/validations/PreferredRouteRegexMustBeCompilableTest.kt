package org.cloudfoundry.promregator.config.validations

import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.Target
import org.junit.Assert
import org.junit.Test
import java.util.*

class PreferredRouteRegexMustBeCompilableTest {

    @Test
    fun testValidDoesNotBreak() {
        val subject = PreferredRouteRegexMustBeCompilable()
        val promregatorConfiguration = PromregatorConfiguration(targets = listOf(Target(preferredRouteRegex = listOf("dummy"))))

        Assert.assertNull(subject.validate(promregatorConfiguration))
    }

    @Test
    fun testInvalidRaisesError() {
        val subject = PreferredRouteRegexMustBeCompilable()

        val promregatorConfiguration = PromregatorConfiguration(targets = listOf(
                Target(preferredRouteRegex = listOf("["))
        ))

        Assert.assertNotNull(subject.validate(promregatorConfiguration))
    }
}