package org.cloudfoundry.promregator.config.validations

import java.util.LinkedList
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration
import org.cloudfoundry.promregator.lite.config.CfTarget
import org.cloudfoundry.promregator.lite.config.TargetAuthenticatorConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class TargetsHaveConsistentAuthenticatorIdTest {
    @Test
    fun testValidateConfigBrokenNotExistingAtAll() {
        val t = CfTarget(authenticatorId = "unittest")
        val targets: MutableList<CfTarget> = LinkedList()
        targets.add(t)
        val pc = PromregatorConfiguration(targets)
        val subject = TargetsHaveConsistentAuthenticatorId()
        val result = subject.validate(pc)
        Assertions.assertNotNull(result)
    }

    @Test
    fun testValidateConfigBrokenWithWrongTAC() {
        val t = CfTarget(authenticatorId = "unittest")
        val targets: MutableList<CfTarget> = LinkedList()
        targets.add(t)
        val tac = TargetAuthenticatorConfiguration()
        tac.id = "unittestOtherIdentifier"
        val tacs: MutableList<TargetAuthenticatorConfiguration> = LinkedList()
        tacs.add(tac)
        val pc = PromregatorConfiguration(targets)
        val subject = TargetsHaveConsistentAuthenticatorId()
        val result = subject.validate(pc)
        Assertions.assertNotNull(result)
    }

    @Test
    fun testValidateConfigOk() {
        val t = CfTarget(authenticatorId = "unittest")
        val tac = TargetAuthenticatorConfiguration().apply { id = "unittest" }
        val pc = PromregatorConfiguration(listOf(t), targetAuthenticators = listOf(tac))
        val subject = TargetsHaveConsistentAuthenticatorId()
        val result = subject.validate(pc)
        Assertions.assertNull(result)
    }
}
