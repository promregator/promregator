package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.config.InvalidTargetProtocolSpecifiedError;
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
	
	@Test
	void testGetProtocolDefault() {
		ResolvedTarget subject = new ResolvedTarget();
		
		subject.setOrgName("testOrgName");
		subject.setSpaceName("testSpaceName");
		subject.setApplicationName("testapp");
		// not setting any protocol
		
		Assertions.assertEquals("https", subject.getProtocol());
	}

	@Test
	void testSetProtocolInvalid() {
		ResolvedTarget subject = new ResolvedTarget();
		
		Assertions.assertThrows(InvalidTargetProtocolSpecifiedError.class, () -> { 
			subject.setProtocol("gopher"); 
		});
		
	}
	
	@Test
	void testHashCodeEquals() {
		ResolvedTarget subject1 = new ResolvedTarget();
		
		subject1.setOrgName("testOrgName");
		subject1.setSpaceName("testSpaceName");
		subject1.setApplicationName("testapp");
		
		subject1.setPath("/path/test");
		subject1.setProtocol("https");
		subject1.setKubernetesAnnotations(true);
		
		
		ResolvedTarget subject2 = new ResolvedTarget();
		
		subject2.setOrgName("testOrgName");
		subject2.setSpaceName("testSpaceName");
		subject2.setApplicationName("testapp");
		
		subject2.setPath("/path/test");
		subject2.setProtocol("https");
		subject2.setKubernetesAnnotations(true);
		
		Assertions.assertEquals(subject1.hashCode(), subject2.hashCode());
	}
	
	@Test
	void testEquals() {
		ResolvedTarget subject1 = new ResolvedTarget();
		
		subject1.setOrgName("testOrgName");
		subject1.setSpaceName("testSpaceName");
		subject1.setApplicationName("testapp");
		
		subject1.setPath("/path/test");
		subject1.setProtocol("https");
		subject1.setKubernetesAnnotations(true);
		
		
		ResolvedTarget subject2 = new ResolvedTarget();
		
		subject2.setOrgName("testOrgName");
		subject2.setSpaceName("testSpaceName");
		subject2.setApplicationName("testapp");
		
		subject2.setPath("/path/test");
		subject2.setProtocol("https");
		subject2.setKubernetesAnnotations(true);
		
		Assertions.assertTrue(subject1.equals(subject2));
	}
	
}
