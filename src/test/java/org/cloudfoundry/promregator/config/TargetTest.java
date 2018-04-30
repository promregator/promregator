package org.cloudfoundry.promregator.config;

import static org.junit.Assert.*;

import org.junit.Test;

public class TargetTest {

	@Test
	public void testClone() throws CloneNotSupportedException {
		Target original = new Target();
		original.setOrgName("orgName");
		original.setSpaceName("spaceName");
		original.setApplicationName("applicationMame");
		original.setPath("path");
		original.setProtocol("http");
		
		Target clone = (Target) original.clone();
		
		assertNotEquals(original, clone);
		assertEquals(original.getOrgName(), clone.getOrgName());
		assertEquals(original.getSpaceName(), clone.getSpaceName());
		assertEquals(original.getApplicationName(), clone.getApplicationName());
		assertEquals(original.getPath(), clone.getPath());
		assertEquals(original.getProtocol(), clone.getProtocol());
	}

}
