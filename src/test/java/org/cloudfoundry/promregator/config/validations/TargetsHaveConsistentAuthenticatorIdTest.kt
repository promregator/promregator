package org.cloudfoundry.promregator.config.validations

import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.config.AuthenticatorConfiguration
import org.cloudfoundry.promregator.config.PromregatorConfiguration
import org.cloudfoundry.promregator.config.Target
import org.junit.jupiter.api.Test

class TargetsHaveConsistentAuthenticatorIdTest {
    @Test
    fun testValidateConfigBrokenNotExistingAtAll() {
        val pc = PromregatorConfiguration(targets = listOf(Target(authenticatorId = "unittest")))

        val subject = TargetsHaveConsistentAuthenticatorId()
        val result = subject.validate(pc)
        assertThat(result).isNotNull()
    }

    @Test
    fun testValidateConfigBrokenWithWrongTAC() {
        val pc = PromregatorConfiguration(targets = listOf(Target(authenticatorId = "unittest")),
                targetAuthenticators = listOf(AuthenticatorConfiguration("unittestOtherIdentifier"))
        )

        val subject = TargetsHaveConsistentAuthenticatorId()
        val result = subject.validate(pc)
        assertThat(result).isNotNull()
    }

    @Test
    fun testValidateConfigOk() {
        val pc = PromregatorConfiguration(targets = listOf(Target(authenticatorId = "unittest")),
                targetAuthenticators = listOf(AuthenticatorConfiguration("unittest"))
        )

        val subject = TargetsHaveConsistentAuthenticatorId()
        val result = subject.validate(pc)
        assertThat(result).isNull()
    }
}