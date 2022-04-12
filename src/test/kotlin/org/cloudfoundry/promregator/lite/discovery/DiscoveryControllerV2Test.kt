package org.cloudfoundry.promregator.lite.discovery

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import org.cloudfoundry.promregator.lite.config.DiscoveryConfig
import org.cloudfoundry.promregator.lite.config.PromregatorConfigurationV2
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@AutoConfigureMockMvc(addFilters = false)
@SpringBootTest(classes = [DiscoveryControllerV2Test.DiscoveryApplication::class],
        properties = ["security.basic.enabled=false"])
internal class DiscoveryControllerV2Test(
        @Autowired private val mockMvc: MockMvc
) {
    @MockkBean private lateinit var discoveryService: DiscoveryService
    @MockkBean private lateinit var promConfiguration: PromregatorConfigurationV2

    @BeforeEach
    fun setup() {
        every { promConfiguration.discovery } returns DiscoveryConfig()
    }

    @Test
    fun discovery() {
        coEvery { discoveryService.discoverCached()} returns listOf()

        mockMvc.get("/v2/discovery")
                .andDo { print() }
                .andExpect {
                    status { isOk() }
                }
    }

    @Test
    fun getPromPort() {
    }

    @Import(DiscoveryControllerV2::class)
    @Configuration
    @EnableWebMvc
    class DiscoveryApplication {

    }

}
