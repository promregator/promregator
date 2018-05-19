package org.cloudfoundry.promregator.scanner;

import org.junit.Assert;
import org.junit.Test;

public class ResolvedTargetTest {

	@Test
	public void testToString() {
		ResolvedTarget subject = new ResolvedTarget();
		
		subject.setOrgName("testOrgName");
		subject.setSpaceName("testSpaceName");
		subject.setApplicationName("testapp");
		
		subject.setPath("/path/test");
		subject.setProtocol("https");
		
		String answer = subject.toString();
		
		Assert.assertTrue(answer.contains("testOrgName"));
		Assert.assertTrue(answer.contains("testSpaceName"));
		Assert.assertTrue(answer.contains("testapp"));
		Assert.assertTrue(answer.contains("/path/test"));
		Assert.assertTrue(answer.contains("https"));
	}

}
