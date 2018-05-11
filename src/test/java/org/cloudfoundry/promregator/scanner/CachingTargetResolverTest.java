package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.scanner.MockedCachingTargetResolverSpringApplication.MockedTargetResolver;
import org.cloudfoundry.promregator.scanner.MockedCachingTargetResolverSpringApplication.RemovalHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.junit.Assert;

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
	
	@Autowired
	private CachingTargetResolverRemovalListener testRemovalHandler;
	
	
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
		
		Assert.assertEquals(2, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assert.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		rt = actualList.get(1);
		Assert.assertEquals(MockedTargetResolver.rTarget2, rt);
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
		
		Assert.assertEquals(2, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assert.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		rt = actualList.get(1);
		Assert.assertEquals(MockedTargetResolver.rTarget2, rt);
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
		
		rt = actualList.get(0);
		Assert.assertEquals(MockedTargetResolver.rTarget1, rt);

		rt = actualList.get(1);
		Assert.assertEquals(MockedTargetResolver.rTarget2, rt);
	}

	@Test
	public void testRemovalHandlerIsCalled() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		
		// fill the cache
		this.cachingTargetResolver.resolveTargets(list);
		
		// ensure that the @After method above did not fool us
		( (RemovalHandler ) this.testRemovalHandler).reset();
		
		this.cachingTargetResolver.invalidateCache();
		
		Assert.assertTrue(( (RemovalHandler ) this.testRemovalHandler).isCalled());
	}
	
}
