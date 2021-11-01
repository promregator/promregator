package org.cloudfoundry.promregator.config.validations

import io.mockk.every
import io.mockk.mockk
import org.mockito.InjectMocks
import org.mockito.Mock
import org.cloudfoundry.promregator.cfaccessor.CFAccessor
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration
import org.cloudfoundry.promregator.lite.config.Target
import org.junit.jupiter.api.BeforeEach
import org.mockito.MockitoAnnotations
import java.util.LinkedList
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

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
        val t = Target(kubernetesAnnotations = true)
        val pc = PromregatorConfiguration(listOf(t))

        every { cfAccessorMock.isV3Enabled } returns false
        val result = sut.validate(pc)
        Assertions.assertNotNull(result)
    }

    @Test
    fun testValidateConfigNoV3NoKubernetes() {
        val t = Target(kubernetesAnnotations = false)
        val pc = PromregatorConfiguration(listOf(t))
        every { cfAccessorMock.isV3Enabled } returns false
        val result = sut.validate(pc)
        Assertions.assertNull(result)
    }

    @Test
    fun testValidateConfigOk() {
        val t = Target(kubernetesAnnotations = true)
        val pc = PromregatorConfiguration(listOf(t))
        every { cfAccessorMock.isV3Enabled } returns true
        val result = sut.validate(pc)
        Assertions.assertNull(result)
    }
}
