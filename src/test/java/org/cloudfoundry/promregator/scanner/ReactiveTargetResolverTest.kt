package org.cloudfoundry.promregator.scanner

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.junit.jupiter.api.AfterEach
import org.mockito.Mockito
import org.cloudfoundry.promregator.cfaccessor.CFAccessor
import org.springframework.beans.factory.annotation.Autowired
import java.util.LinkedList
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock
import reactor.core.publisher.Mono
import org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl
import org.junit.jupiter.api.AfterAll
import org.cloudfoundry.promregator.JUnitTestUtils
import org.cloudfoundry.promregator.lite.config.CfTarget
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [MockedReactiveTargetResolverSpringApplication::class])
internal class ReactiveTargetResolverTest {
    @AfterEach
    fun resetCFAccessorMock() {
        Mockito.reset(cfAccessor)
    }

    @Autowired
    private val targetResolver: TargetResolver? = null

    @Autowired
    lateinit var cfAccessor: CFAccessor
    @Test
    fun testFullyResolvedAlready() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals(t.applicationName, rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
        Mockito.verify(cfAccessor, Mockito.times(0))!!.retrieveSpaceSummary(CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testMissingApplicationName() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(4, actualList.size)
        var rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        rt = actualList[1]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithApplicationRegex() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = ".*2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithApplicationRegexCaseInsensitiveIssue76() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = "te.*App2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testEmpty() {
        val list: List<CfTarget> = LinkedList()
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertNotNull(actualList)
        Assertions.assertEquals(0, actualList.size)
    }

    @Test
    fun testSummaryDoesnotExist() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace-summarydoesnotexist",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(0, actualList.size)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID_DOESNOTEXIST)
    }

    @Test
    fun testRetrieveAllApplicationIdsInSpaceThrowsException() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace-summaryexception",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(0, actualList.size)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID_EXCEPTION)
    }

    @Test
    fun testInvalidOrgNameToResolve() {
        val list: MutableList<CfTarget> = LinkedList()
        var t = CfTarget(
                orgName = "doesnotexist",
                spaceName = "unittestspace",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals(t.applicationName, rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
    }

    @Test
    fun testExceptionOrgNameToResolve() {
        val list: MutableList<CfTarget> = LinkedList()
        var t = CfTarget(
                orgName = "exception",
                spaceName = "unittestspace",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals(t.applicationName, rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
    }

    @Test
    fun testInvalidSpaceNameToResolve() {
        val list: MutableList<CfTarget> = LinkedList()
        var t = CfTarget(
                orgName = "unittestorg",
                spaceName = "doesnotexist",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals(t.applicationName, rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
    }

    @Test
    fun testExceptionSpaceNameToResolve() {
        val list: MutableList<CfTarget> = LinkedList()
        var t = CfTarget(
                orgName = "unittestorg",
                spaceName = "exception",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals(t.applicationName, rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
    }

    @Test
    fun testDistinctResolvedTargets() {
        val list: MutableList<CfTarget> = LinkedList()
        var t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = "testapp.*",
                // Note that this will find all of testapp, testapp2 and testapp3
                path = "path",
                protocol = "https"
        )
        list.add(t)
        t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp",
                // this is causing the double-selection
                path = "path",
                protocol = "https"
        )
        list.add(t)

        // NB: The regex target (first one) overlaps with the already fully resolved target (second one)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(3, actualList.size) // and not 4!
        var testappFound = false
        var testapp2Found = false
        var testapp3Found = false
        for (rt in actualList) {
            Assertions.assertEquals(t.orgName, rt.orgName)
            Assertions.assertEquals(t.spaceName, rt.spaceName)
            Assertions.assertEquals(t.path, rt.path)
            Assertions.assertEquals(t.protocol, rt.protocol)
            if (rt.applicationName == "testapp2") {
                testapp2Found = true
            } else if (rt.applicationName == "testapp") {
                if (testappFound) {
                    // testApp was already found before; this would be a duplicate entry, which is what we want to avoid
                    Assertions.fail<Any>("Duplicate entry for testapp returned")
                }
                testappFound = true
            } else if (rt.applicationName == "testapp3") {
                testapp3Found = true
            } else {
                Assertions.fail<Any>("Unknown application name returned")
            }
        }
        Assertions.assertTrue(testappFound)
        Assertions.assertTrue(testapp2Found)
        Assertions.assertTrue(testapp3Found)
    }

    @Test
    fun testMissingOrgName() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                spaceName = "unittestspace",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals("unittestorg", rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllOrgIds()
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithOrgRegex() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgRegex = "unittest.*",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals("unittestorg", rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllOrgIds()
        Mockito.verify(cfAccessor, Mockito.times(1))!!.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithOrgRegexCaseInsensitive() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgRegex = "unit.*TOrg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals("unittestorg", rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testMissingSpaceName() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals("unittestorg", rt.orgName)
        Assertions.assertEquals("unittestspace", rt.spaceName)
        Assertions.assertEquals("testapp", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveSpaceIdsInOrg(CFAccessorMock.UNITTEST_ORG_UUID)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithSpaceRegex() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceRegex = "unitte.*",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals("unittestorg", rt.orgName)
        Assertions.assertEquals("unittestspace", rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveSpaceIdsInOrg(CFAccessorMock.UNITTEST_ORG_UUID)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithSpaceRegexCaseInsensitive() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceRegex = "unit.*tSpace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals("unittestorg", rt.orgName)
        Assertions.assertEquals("unittestspace", rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveSpaceIdsInOrg(CFAccessorMock.UNITTEST_ORG_UUID)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testCorrectingCaseOnNamesIssue77() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "uniTtEstOrg",
                spaceName = "unitteStspAce",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals("unittestorg", rt.orgName)
        Assertions.assertEquals("unittestspace", rt.spaceName)
        Assertions.assertEquals("testapp", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
    }

    @Test
    fun testInvalidOrgNameDoesNotRaiseExceptionIssue109() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "doesnotexist",
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(0, actualList.size)
    }

    @Test
    fun testInvalidSpaceNameDoesNotRaiseExceptionIssue109() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "doesnotexist",
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(0, actualList.size)
    }

    @Test
    fun testWithV3Unsupported() {
        val errorResponse = Mono.just(ReactiveCFAccessorImpl.INVALID_APPLICATIONS_RESPONSE)
        Mockito.`when`(cfAccessor!!.retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID))
                .thenReturn(errorResponse)
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = ".*2",
                path = "path",
                protocol = "https",
                kubernetesAnnotations = true
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)

        // Still returns 1 even though the annotations do not exist
        Assertions.assertEquals(1, actualList.size)
        val rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp2", rt.applicationName)
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID,
                CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithAnnotationsUnexpectedValue() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = ".*2",
                path = "path",
                protocol = "https",
                kubernetesAnnotations = true,
        )
        list.add(t)
        // testapp2 has an invalid scrape value
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(0, actualList.size)
        Mockito.verify(cfAccessor, Mockito.times(1)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID,
                CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    @Test
    fun testWithAnnotations() {
        val list: MutableList<CfTarget> = LinkedList()
        val t = CfTarget(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = ".*",
                path = "path",
                protocol = "https",
                kubernetesAnnotations = true,
        )
        list.add(t)
        val actualList = targetResolver!!.resolveTargets(list)
        Assertions.assertEquals(2, actualList.size)
        var rt = actualList[0]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("testapp", rt.applicationName)
        // Defaults pathing to config value
        Assertions.assertEquals(t.path, rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        rt = actualList[1]
        Assertions.assertEquals(t, rt.originalTarget)
        Assertions.assertEquals(t.orgName, rt.orgName)
        Assertions.assertEquals(t.spaceName, rt.spaceName)
        Assertions.assertEquals("internalapp", rt.applicationName)
        // Overrides pathing with annotations
        Assertions.assertEquals("/actuator/prometheus", rt.path)
        Assertions.assertEquals(t.protocol, rt.protocol)
        Mockito.verify(cfAccessor, Mockito.times(4)).retrieveAllApplicationsInSpaceV3(CFAccessorMock.UNITTEST_ORG_UUID,
                CFAccessorMock.UNITTEST_SPACE_UUID)
    }

    companion object {
        @AfterAll
        fun cleanupEnvironment() {
            JUnitTestUtils.cleanUpAll()
        }
    }
}
