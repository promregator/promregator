package org.cloudfoundry.promregator.config;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;


public class TargetTest {

	@Test
	public void testLabelsAllValuesNull() {
		Target subject = new Target();
		
		String[] actuals = subject.labelValues();
		
		for (String actual : actuals) {
			Assert.assertNotNull(actual);
		}
	}
	
	@Test
	public void testLabelValuesFitToLabelNames() {
		Target subject = new Target();
		setAllTargetAttributes(subject);
		
		int labelValuesCount = subject.labelValues().length;
		int labelNamesCount = Target.labelNames().length;
		
		Assert.assertEquals(labelValuesCount, labelNamesCount);
	}

	private static void setAllTargetAttributes(Target t) {
		t.setOrgName("DummyOrg");
		t.setOrgRegex("OrgRegex");
		t.setSpaceName("DummySpace");
		t.setSpaceRegex("SpaceRegex");
		t.setApplicationName("DummyApp");
		t.setApplicationRegex("AppRegx");
		t.setAuthenticatorId("authId");
		t.setPath("/path");
		t.setProtocol("https");
		
		final String[] routeRegex = { "abc", ".*xyz.*" };
		t.setPreferredRouteRegex(new ArrayList<String>(Arrays.asList(routeRegex)));
		// Note that it is important, that we get a different instance for a list each time! We must compare contents and not references!
	}
	
	@Test
	public void equalityTests() {
		Target o1 = new Target();
		setAllTargetAttributes(o1);
		
		Target o2 = new Target();
		setAllTargetAttributes(o2);
		
		Assert.assertTrue(o1.equals(o2));
		Assert.assertEquals(o1.hashCode(), o2.hashCode());
	}
	
}
