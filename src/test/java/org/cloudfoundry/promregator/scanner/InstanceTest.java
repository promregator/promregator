package org.cloudfoundry.promregator.scanner;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InstanceTest {

	@Test
	void testToString() {
		ResolvedTarget rt = new ResolvedTarget();
		
		rt.setOrgName("testOrgName");
		rt.setSpaceName("testSpaceName");
		rt.setApplicationName("testapp");
		rt.setPath("/test/path");
		rt.setProtocol("https");
		
		Instance subject = new Instance(rt, "1", "https://accessUrl.bogus", false);
		
		String result = subject.toString();
		
		Assertions.assertTrue(result.contains("testOrgName"));
		Assertions.assertTrue(result.contains("testSpaceName"));
		Assertions.assertTrue(result.contains("testapp"));
		Assertions.assertTrue(result.contains("/test/path"));
		Assertions.assertTrue(result.contains("https"));
		Assertions.assertTrue(result.contains("https://accessUrl.bogus"));
		Assertions.assertTrue(result.contains("false"));
		
	}

}
