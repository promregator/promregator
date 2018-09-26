package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.scanner.MockedCachingTargetResolverSpringApplication.MockedTargetResolver;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedCachingTargetResolverSpringApplication.class)
public class CachingTargetResolverTest {
	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Autowired
	private TargetResolver targetResolver;
	
	@Autowired
	private CachingTargetResolver cachingTargetResolver;
	
	@After
	public void resetFlags() {
		this.cachingTargetResolver.invalidateCache();
		
		( (MockedTargetResolver) targetResolver).resetRequestFlags();
		
	}
	@Test
	public void testTwoPlainTargets() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		list.add(MockedTargetResolver.target2);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assert.assertTrue(mtr.isRequestForTarget1());
		Assert.assertTrue(mtr.isRequestForTarget2());
		Assert.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assert.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assert.assertEquals(2, actualList.size());
		
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assert.fail("Unexpected target provided");
			}
		}
		Assert.assertTrue(target1Found);
		Assert.assertTrue(target2Found);
	}
	
	@Test
	public void testTargetMissingApplicationName() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.targetAllInSpace);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assert.assertFalse(mtr.isRequestForTarget1());
		Assert.assertFalse(mtr.isRequestForTarget2());
		Assert.assertTrue(mtr.isRequestForTargetAllInSpace());
		Assert.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assert.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assert.fail("Unexpected target provided");
			}
		}
		Assert.assertTrue(target1Found);
		Assert.assertTrue(target2Found);
	}
	
	@Test
	public void testRepeatedRequestIsCached() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		
		// fill the cache
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assert.assertTrue(mtr.isRequestForTarget1());
		Assert.assertFalse(mtr.isRequestForTarget2());
		Assert.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assert.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assert.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assert.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		
		mtr.resetRequestFlags();
		
		actualList = this.cachingTargetResolver.resolveTargets(list);
		Assert.assertFalse(mtr.isRequestForTarget1());
		Assert.assertFalse(mtr.isRequestForTarget2());
		Assert.assertFalse(mtr.isRequestForTargetAllInSpace());
		
		Assert.assertEquals(1, actualList.size());
		
		rt = actualList.get(0);
		Assert.assertEquals(MockedTargetResolver.rTarget1, rt);

	}
	
	@Test
	public void testRepeatedRequestIsCachedAlsoSelectively() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		
		// fill the cache
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assert.assertTrue(mtr.isRequestForTarget1());
		Assert.assertFalse(mtr.isRequestForTarget2());
		Assert.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assert.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assert.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assert.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		
		mtr.resetRequestFlags();
		
		list.add(MockedTargetResolver.target2);
		
		actualList = this.cachingTargetResolver.resolveTargets(list);
		Assert.assertFalse(mtr.isRequestForTarget1());
		Assert.assertTrue(mtr.isRequestForTarget2());
		Assert.assertFalse(mtr.isRequestForTargetAllInSpace());
		
		Assert.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt2 : actualList) {
			if (rt2 == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt2 == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assert.fail("Unexpected target provided");
			}
		}
		Assert.assertTrue(target1Found);
		Assert.assertTrue(target2Found);
	}
	
	@Test
	public void testTargetDuplicateRequestDistincts() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		list.add(MockedTargetResolver.targetRegex);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list);
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assert.assertTrue(mtr.isRequestForTarget1());
		Assert.assertFalse(mtr.isRequestForTarget2());
		Assert.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assert.assertTrue(mtr.isRequestForTargetWithRegex());
		
		Assert.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assert.fail("Unexpected target provided");
			}
		}
		Assert.assertTrue(target1Found);
		Assert.assertTrue(target2Found);
	}
}
