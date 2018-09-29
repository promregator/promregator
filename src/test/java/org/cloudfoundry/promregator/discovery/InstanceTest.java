package org.cloudfoundry.promregator.discovery;

import org.cloudfoundry.promregator.discovery.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.junit.Assert;
import org.junit.Test;

public class InstanceTest {

	@Test
	public void testToString() {
		ResolvedTarget rt = new ResolvedTarget();
		
		rt.setOrgName("testOrgName");
		rt.setSpaceName("testSpaceName");
		rt.setApplicationName("testapp");
		rt.setPath("/test/path");
		rt.setProtocol("https");
		
		Instance subject = new Instance(rt, "1", "https://accessUrl.bogus");
		
		String result = subject.toString();
		
		Assert.assertTrue(result.contains("testOrgName"));
		Assert.assertTrue(result.contains("testSpaceName"));
		Assert.assertTrue(result.contains("testapp"));
		Assert.assertTrue(result.contains("/test/path"));
		Assert.assertTrue(result.contains("https"));
		Assert.assertTrue(result.contains("https://accessUrl.bogus"));
		
	}

}
