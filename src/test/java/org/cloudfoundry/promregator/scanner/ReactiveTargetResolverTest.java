package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.config.Target;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedReactiveTargetResolverSpringApplication.class)
public class ReactiveTargetResolverTest {

	@Autowired
	private TargetResolver targetResolver;

	@Test
	public void testFullyResolvedAlready() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("testapp");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assert.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assert.assertEquals(t, rt.getOriginalTarget());
		Assert.assertEquals(t.getOrgName(), rt.getOrgName());
		Assert.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assert.assertEquals(t.getApplicationName(), rt.getApplicationName());
		Assert.assertEquals(t.getPath(), rt.getPath());
		Assert.assertEquals(t.getProtocol(), rt.getProtocol());
	}
	
	@Test
	public void testMissingApplicationName() {
		List<Target> list = new LinkedList<>();
		
		Target t = new Target();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setPath("path");
		t.setProtocol("https");
		list.add(t);
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assert.assertEquals(2, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assert.assertEquals(t, rt.getOriginalTarget());
		Assert.assertEquals(t.getOrgName(), rt.getOrgName());
		Assert.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assert.assertEquals("testapp", rt.getApplicationName());
		Assert.assertEquals(t.getPath(), rt.getPath());
		Assert.assertEquals(t.getProtocol(), rt.getProtocol());
		
		rt = actualList.get(1);
		Assert.assertEquals(t, rt.getOriginalTarget());
		Assert.assertEquals(t.getOrgName(), rt.getOrgName());
		Assert.assertEquals(t.getSpaceName(), rt.getSpaceName());
		Assert.assertEquals("testapp2", rt.getApplicationName());
		Assert.assertEquals(t.getPath(), rt.getPath());
		Assert.assertEquals(t.getProtocol(), rt.getProtocol());
	}

	@Test
	public void testEmpty() {
		
		List<Target> list = new LinkedList<>();
		
		List<ResolvedTarget> actualList = this.targetResolver.resolveTargets(list);
		
		Assert.assertNotNull(actualList);
		Assert.assertEquals(0, actualList.size());
	}
	
}
