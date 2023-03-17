package org.cloudfoundry.promregator.cfaccessor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CacheKeyApplicationTest {

	@Test
	public void testGetterSetter() {
		CacheKeyApplication subject = new CacheKeyApplication("orgid", "spaceid", "appname");
		Assertions.assertEquals("orgid", subject.getOrgId());
		Assertions.assertEquals("spaceid", subject.getSpaceId());
		Assertions.assertEquals("appname", subject.getApplicationName());
	}
	
	@Test
	public void testHashCodeEquals() {
		CacheKeyApplication subject1 = new CacheKeyApplication("orgid", "spaceid", "appname");
		CacheKeyApplication subject2 = new CacheKeyApplication("orgid", "spaceid", "appname");
		
		Assertions.assertEquals(subject1.hashCode(), subject2.hashCode());
	}
	
	@Test
	public void testEquals() {
		CacheKeyApplication subject1 = new CacheKeyApplication("orgid", "spaceid", "appname");
		CacheKeyApplication subject2 = new CacheKeyApplication("orgid", "spaceid", "appname");
		
		Assertions.assertTrue(subject1.equals(subject2));
	}
	
	@Test
	public void testToStringSimple() {
		CacheKeyApplication subject = new CacheKeyApplication("orgid", "spaceid", "appname");
		
		String toString = subject.toString();
		Assertions.assertTrue(toString.contains("orgid"));
		Assertions.assertTrue(toString.contains("spaceid"));
		Assertions.assertTrue(toString.contains("appname"));
	}

}
