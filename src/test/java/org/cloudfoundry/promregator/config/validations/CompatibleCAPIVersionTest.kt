package org.cloudfoundry.promregator.config.validations

import io.mockk.every
import io.mockk.mockk
import org.cloudfoundry.promregator.cfaccessor.CFAccessor
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration
import org.cloudfoundry.promregator.lite.config.CfTarget
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class CompatibleCAPIVersionTest {
    lateinit var sut: CompatibleCAPIVersion

    lateinit var cfAccessorMock: CFAccessor
    @BeforeEach
    fun initMocks() {
        cfAccessorMock = mockk()
        sut = CompatibleCAPIVersion(cfAccessorMock)
    }

    @Test
    fun testValidateConfigNoV3() {
        val t = CfTarget(kubernetesAnnotations = true)
        val pc = PromregatorConfiguration(listOf(t))

        every { cfAccessorMock.isV3Enabled } returns false
        val result = sut.validate(pc)
        Assertions.assertNotNull(result)
    }

    @Test
    fun testValidateConfigNoV3NoKubernetes() {
        val t = CfTarget(kubernetesAnnotations = false)
        val pc = PromregatorConfiguration(listOf(t))
        every { cfAccessorMock.isV3Enabled } returns false
        val result = sut.validate(pc)
        Assertions.assertNull(result)
    }

    @Test
    fun testValidateConfigOk() {
        val t = CfTarget(kubernetesAnnotations = true)
        val pc = PromregatorConfiguration(listOf(t))
        every { cfAccessorMock.isV3Enabled } returns true
        val result = sut.validate(pc)
        Assertions.assertNull(result)
    }
}
