package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.scanner.MockedCachingTargetResolverSpringApplication.MockedTargetResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedCachingTargetResolverSpringApplication.class)
class CachingTargetResolverTest {
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Autowired
	private TargetResolver targetResolver;
	
	@Autowired
	private CachingTargetResolver cachingTargetResolver;
	
	@AfterEach
	void resetFlags() {
		this.cachingTargetResolver.invalidateCache();
		
		( (MockedTargetResolver) targetResolver).resetRequestFlags();
		
	}
	@Test
	void testTwoPlainTargets() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		list.add(MockedTargetResolver.target2);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertTrue(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(2, actualList.size());
		
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}
	
	@Test
	void testTargetMissingApplicationName() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.targetAllInSpace);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertFalse(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertTrue(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}
	
	@Test
	void testRepeatedRequestIsCached() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		
		// fill the cache
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		
		mtr.resetRequestFlags();
		
		actualList = this.cachingTargetResolver.resolveTargets(list);
		Assertions.assertFalse(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		
		Assertions.assertEquals(1, actualList.size());
		
		rt = actualList.get(0);
		Assertions.assertEquals(MockedTargetResolver.rTarget1, rt);

	}
	
	@Test
	void testRepeatedRequestIsCachedAlsoSelectively() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		
		// fill the cache
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		
		mtr.resetRequestFlags();
		
		list.add(MockedTargetResolver.target2);
		
		actualList = this.cachingTargetResolver.resolveTargets(list);
		Assertions.assertFalse(mtr.isRequestForTarget1());
		Assertions.assertTrue(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		
		Assertions.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt2 : actualList) {
			if (rt2 == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt2 == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}
	
	@Test
	void testTargetDuplicateRequestDistincts() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		list.add(MockedTargetResolver.targetRegex);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertTrue(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}
}
