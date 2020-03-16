package org.cloudfoundry.promregator.scanner

import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.cfaccessor.CFAccessor
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock
import org.cloudfoundry.promregator.config.Target
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.function.Predicate

class ReactiveAppInstanceScannerTest {
    private lateinit var appInstanceScanner: ReactiveAppInstanceScanner
    private lateinit var cfAccessor: CFAccessor

    @BeforeEach
    fun setup() {
        cfAccessor = CFAccessorMock()
        appInstanceScanner = ReactiveAppInstanceScanner(cfAccessor)
    }

    @Test
    fun testStraightForward() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testWithPrefiltering() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, Predicate { instance: Instance ->
            !instance.instanceId.startsWith(CFAccessorMock.UNITTEST_APP1_UUID) // the instances of app1 are being filtered away
        })
        assertThat(result).`as`("should have been filtered").extracting("instanceId").doesNotContain(CFAccessorMock.UNITTEST_APP1_UUID + ":0")
        assertThat(result).`as`("should have been filtered").extracting("instanceId").doesNotContain(CFAccessorMock.UNITTEST_APP1_UUID + ":1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testWithWrongCaseIssue76() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testApp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testEmptyResponseOnOrg() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "doesnotexist"
        t.spaceName = "shouldneverbeused"
        t.applicationName = "shouldneverbeused"
        t.path = "/shouldneverbeused"
        t.protocol = "https"
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testEmptyResponseOnSpace() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "doesnotexist"
        t.applicationName = "shouldneverbeused"
        t.path = "/shouldneverbeused"
        t.originalTarget = emptyTarget
        t.protocol = "https"
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testEmptyResponseOnApplicationId() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "doesnotexist"
        t.path = "/shouldneverbeused"
        t.protocol = "https"
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testEmptyResponseOnSummary() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace-summarydoesnotexist"
        t.applicationName = "testapp"
        t.path = "/shouldneverbeused"
        t.protocol = "https"
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testExceptionOnOrg() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "exception"
        t.spaceName = "shouldneverbeused"
        t.applicationName = "shouldneverbeused"
        t.path = "/shouldneverbeused"
        t.protocol = "https"
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).`as`("targets with exceptions are ignored").extracting("target.applicationName").doesNotContain("shouldneverbeused")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testExceptionOnSpace() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "exception"
        t.applicationName = "shouldneverbeused"
        t.path = "/shouldneverbeused"
        t.protocol = "https"
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testExceptionOnApplicationId() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "exception"
        t.path = "/shouldneverbeused"
        t.protocol = "https"
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testExceptionOnSummary() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace-summaryexception"
        t.applicationName = "testapp"
        t.path = "/shouldneverbeused"
        t.protocol = "https"
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        t.originalTarget = emptyTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testMatchPreferredRouteRegex() {
        val targets: MutableList<ResolvedTarget> = LinkedList()
        var t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp"
        t.path = "/testpath1"
        t.protocol = "http"
        t.applicationId = CFAccessorMock.UNITTEST_APP1_UUID
        val emptyTarget = Target()
        t.originalTarget = emptyTarget
        targets.add(t)
        t = ResolvedTarget()
        t.orgName = "unittestorg"
        t.spaceName = "unittestspace"
        t.applicationName = "testapp2"
        t.path = "/testpath2"
        t.protocol = "https"
        t.applicationId = CFAccessorMock.UNITTEST_APP2_UUID
        val origTarget = Target(preferredRouteRegex = listOf(".*additionalSubdomain.*"))
        t.originalTarget = origTarget
        targets.add(t)
        val result = appInstanceScanner!!.determineInstancesFromTargets(targets, null, null)
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.additionalSubdomain.shared.domain.example.org/additionalPath/testpath2")
    }

    @Test
    fun testMatchPreferredRouteRegexNotMatched() {
        val targets = listOf(
                ResolvedTarget().apply {
                    orgName = "unittestorg"
                    spaceName = "unittestspace"
                    applicationName = "testapp"
                    path = "/testpath1"
                    protocol = "http"
                    applicationId = CFAccessorMock.UNITTEST_APP1_UUID
                    originalTarget = Target()
                },
                ResolvedTarget().apply {
                    orgName = "unittestorg"
                    spaceName = "unittestspace"
                    applicationName = "testapp2"
                    path = "/testpath2"
                    protocol = "https"
                    applicationId = CFAccessorMock.UNITTEST_APP2_UUID
                    originalTarget = Target(preferredRouteRegex = listOf(".*notMatched.*"))
                }
        )

        val result = appInstanceScanner.determineInstancesFromTargets(targets, null, null)

        assertThat(result.filter { it.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":0" }.map { it.accessUrl })
                .containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP1_UUID + ":1" }
                .extracting("accessUrl").containsOnly("http://hostapp1.shared.domain.example.org/testpath1")
        assertThat(result).filteredOn { instance: Instance -> instance.instanceId == CFAccessorMock.UNITTEST_APP2_UUID + ":0" }
                .extracting("accessUrl").containsOnly("https://hostapp2.shared.domain.example.org/additionalPath/testpath2")
    }

}