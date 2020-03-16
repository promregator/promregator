package org.cloudfoundry.promregator.scanner

import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.cloudfoundry.promregator.cfaccessor.CFAccessor
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock
import org.cloudfoundry.promregator.config.Target
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.*

class ReactiveTargetResolverTest {
    private lateinit var targetResolver: ReactiveTargetResolver
    private lateinit var cfAccessor: CFAccessor

    @BeforeEach
    fun setup() {
        cfAccessor = spyk(CFAccessorMock())
        targetResolver = ReactiveTargetResolver(cfAccessor)
    }

    @Test
    fun testFullyResolvedAlready() {
        val list: MutableList<Target> = LinkedList()
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )

        list.add(t)
        val actualList = targetResolver.resolveTargets(list)
        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo(t.applicationName)
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }


        verify(exactly = 1) { cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
        verify(exactly = 0) { cfAccessor.retrieveSpaceSummary(CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testMissingApplicationName() {
        val t = Target(orgName = "unittestorg",
                spaceName = "unittestspace",
                path = "path",
                protocol = "https")
        val list = listOf(t)

        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(2)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo("testapp")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        with(actualList[1]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo("testapp2")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) { cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testWithApplicationRegex() {
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = ".*2",
                path = "path",
                protocol = "https"
        )
        val list = listOf(t)

        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo("testapp2")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testWithApplicationRegexCaseInsensitiveIssue76() {
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = "te.*App2",
                path = "path",
                protocol = "https"
        )
        val list = listOf(t)

        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo("testapp2")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testEmpty() {
        val actualList = targetResolver.resolveTargets(listOf())

        assertThat(actualList).hasSize(0)
    }

    @Test
    fun testSummaryDoesnotExist() {
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace-summarydoesnotexist",
                path = "path",
                protocol = "https"
        )
        val list = listOf(t)

        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(0)

        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID_DOESNOTEXIST) }
    }

    @Test
    fun testRetrieveAllApplicationIdsInSpaceThrowsException() {
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace-summaryexception",
                path = "path",
                protocol = "https"
        )
        val list = listOf(t)

        val actualList = targetResolver.resolveTargets(list)
        assertThat(actualList).hasSize(0)

        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID_EXCEPTION) }
    }

    @Test
    fun testInvalidOrgNameToResolve() {
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )

        val list = listOf(
                Target(
                        orgName = "doesnotexist",
                        spaceName = "unittestspace",
                        path = "path",
                        protocol = "https"
                ), t
        )

        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo(t.applicationName)
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

    }

    @Test
    fun testExceptionOrgNameToResolve() {
        val tBadOrg = Target(
                orgName = "exception",
                spaceName = "unittestspace",
                path = "path",
                protocol = "https"
        )
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        val list = listOf(tBadOrg, t)

        val actualList = targetResolver.resolveTargets(list)
        assertThat(actualList).hasSize(1)

        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo(t.applicationName)
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

    }

    @Test
    fun testInvalidSpaceNameToResolve() {
        val tInvalidSpaceName = Target(
                orgName = "unittestorg",
                spaceName = "doesnotexist",
                path = "path",
                protocol = "https"
        )
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )
        val list = listOf(tInvalidSpaceName, t)

        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo(t.applicationName)
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }
    }

    @Test
    fun testExceptionSpaceNameToResolve() {
        val tBadSpaceName = Target(
                orgName = "unittestorg",
                spaceName = "exception",
                path = "path",
                protocol = "https"
        )
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https")
        val list = listOf(tBadSpaceName, t)

        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo(t.applicationName)
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }
    }

    @Test
    fun testDistinctResolvedTargets() {
        val tRegex = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationRegex = "testapp.*",
                path = "path",
                protocol = "https"
        )
        val t = Target(
                orgName = "unittestorg",
                spaceName = "unittestspace",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )
        val list = listOf(tRegex, t)

        // NB: The regex target (first one) overlaps with the already fully resolved target (second one)
        val actualList = targetResolver.resolveTargets(list)

        assertThat(actualList).hasSize(2) // and not 3!
        val rt1 = actualList.firstOrNull {it.applicationName == "testapp"} ?: fail("testapp was expected")
        val rt2 = actualList.firstOrNull {it.applicationName == "testapp2"} ?: fail("testapp2 was expected")
        with(rt1) {
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }
        with(rt2) {
            assertThat(orgName).isEqualTo(t.orgName)
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }
    }

    @Test
    fun testMissingOrgName() {
        val t = Target(
                spaceName = "unittestspace",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo("testapp")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveAllOrgIds() }
        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testWithOrgRegex() {
        val t = Target(
                orgRegex = "unittest.*",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo("testapp2")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveAllOrgIds() }
        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testWithOrgRegexCaseInsensitive() {
        val t = Target(
                orgRegex = "unit.*TOrg",
                spaceName = "unittestspace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo(t.spaceName)
            assertThat(applicationName).isEqualTo("testapp2")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testMissingSpaceName() {
        val t = Target(
                orgName = "unittestorg",
                applicationName = "testapp",
                path = "path",
                protocol = "https"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(applicationName).isEqualTo("testapp")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveSpaceIdsInOrg(CFAccessorMock.UNITTEST_ORG_UUID) }
        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testWithSpaceRegex() {
        val t = Target(
                orgName = "unittestorg",
                spaceRegex = "unitte.*",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(applicationName).isEqualTo("testapp2")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveSpaceIdsInOrg(CFAccessorMock.UNITTEST_ORG_UUID) }
        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testWithSpaceRegexCaseInsensitive() {
        val t = Target(
                orgName = "unittestorg",
                spaceRegex = "unit.*tSpace",
                applicationName = "testapp2",
                path = "path",
                protocol = "https"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(applicationName).isEqualTo("testapp2")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }

        verify(exactly = 1) {cfAccessor.retrieveSpaceIdsInOrg(CFAccessorMock.UNITTEST_ORG_UUID) }
        verify(exactly = 1) {cfAccessor.retrieveAllApplicationIdsInSpace(CFAccessorMock.UNITTEST_ORG_UUID, CFAccessorMock.UNITTEST_SPACE_UUID) }
    }

    @Test
    fun testCorrectingCaseOnNamesIssue77() {
        val t = Target(
                orgName = "uniTtEstOrg",
                spaceName = "unitteStspAce",
                applicationName = "testApp",
                path = "path",
                protocol = "https"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))

        assertThat(actualList).hasSize(1)
        with(actualList[0]) {
            assertThat(originalTarget).isEqualTo(t)
            assertThat(orgName).isEqualTo("unittestorg")
            assertThat(spaceName).isEqualTo("unittestspace")
            assertThat(applicationName).isEqualTo("testapp")
            assertThat(path).isEqualTo(t.path)
            assertThat(protocol).isEqualTo(t.protocol)
        }
    }

    @Test
    fun testInvalidOrgNameDoesNotRaiseExceptionIssue109() {
        val t = Target(orgName = "doesnotexist")

        val actualList = targetResolver.resolveTargets(listOf(t))
        assertThat(actualList).isEmpty()
    }

    @Test
    fun testInvalidSpaceNameDoesNotRaiseExceptionIssue109() {
        val t = Target(
                orgName = "unittestorg",
                spaceName = "doesnotexist"
        )

        val actualList = targetResolver.resolveTargets(listOf(t))
        assertThat(actualList).isEmpty()
    }

}