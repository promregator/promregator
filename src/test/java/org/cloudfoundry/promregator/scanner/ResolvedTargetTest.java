package org.cloudfoundry.promregator.scanner;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ResolvedTargetTest {

	@Test
	void testToString() {
		ResolvedTarget subject = new ResolvedTarget();
		
		subject.setOrgName("testOrgName");
		subject.setSpaceName("testSpaceName");
		subject.setApplicationName("testapp");
		
		subject.setPath("/path/test");
		subject.setProtocol("https");
		subject.setKubernetesAnnotations(true);
		
		String answer = subject.toString();
		
		Assertions.assertTrue(answer.contains("testOrgName"));
		Assertions.assertTrue(answer.contains("testSpaceName"));
		Assertions.assertTrue(answer.contains("testapp"));
		Assertions.assertTrue(answer.contains("/path/test"));
		Assertions.assertTrue(answer.contains("https"));
		Assertions.assertTrue(answer.contains("kuberntesAnnotations=true"));
	}

}
