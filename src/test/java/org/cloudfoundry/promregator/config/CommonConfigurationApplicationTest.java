package org.cloudfoundry.promregator.config;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import io.jsonwebtoken.lang.Collections;

public class CommonConfigurationApplicationTest {

	@Test
	public void testCopyFromCommonConfigurationApplicationOverwritesIfProvided() {
		String[] sourceRoutesArray = { "abc", "def" };
		List<String> sourceRoutes = Collections.arrayToList(sourceRoutesArray);
		CommonConfigurationApplication source = new CommonConfigurationApplication("appName", "appRegex", "/path", "http", "authId", sourceRoutes);
		
		String[] targetRoutesArray= { "xyz" };
		List<String> targetRoutes = Collections.arrayToList(targetRoutesArray);
		CommonConfigurationApplication target = new CommonConfigurationApplication("!appName", "!appRegex", "!/path", "https", "!authId", targetRoutes);
		
		target.copyFromCommonConfigurationApplication(source);
		
		Assert.assertEquals("appName", target.getApplicationName());
		Assert.assertEquals("appRegex", target.getApplicationRegex());
		Assert.assertEquals("/path", target.getPathOrDefault());
		Assert.assertEquals("http", target.getProtocolOrDefault());
		Assert.assertEquals(sourceRoutes, target.getPreferredRouteRegex());
	}
	
	@Test
	public void testCopyFromCommonConfigurationApplicationKeepsIfEmpty() {
		CommonConfigurationApplication source = new CommonConfigurationApplication(null, null, null, null, null, null);
		
		String[] targetRoutesArray = { "abc", "def" };
		List<String> targetRoutes = Collections.arrayToList(targetRoutesArray);
		CommonConfigurationApplication target = new CommonConfigurationApplication("appName", "appRegex", "/path", "http", "authId", targetRoutes);
		
		target.copyFromCommonConfigurationApplication(source);
		
		Assert.assertEquals("appName", target.getApplicationName());
		Assert.assertEquals("appRegex", target.getApplicationRegex());
		Assert.assertEquals("/path", target.getPathOrDefault());
		Assert.assertEquals("http", target.getProtocolOrDefault());
		Assert.assertEquals(targetRoutes, target.getPreferredRouteRegex());
	}

}
