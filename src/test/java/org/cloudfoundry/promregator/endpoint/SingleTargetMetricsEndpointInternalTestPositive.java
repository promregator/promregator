package org.cloudfoundry.promregator.endpoint;

import java.util.function.Predicate;

import org.cloudfoundry.promregator.discovery.ConfigurationTargetInstance;
import org.cloudfoundry.promregator.discovery.Instance;
import org.junit.Assert;
import org.junit.Test;

public class SingleTargetMetricsEndpointInternalTestPositive extends SingleTargetMetricsEndpoint {

	@Override
	public String handleRequest(Predicate<? super String> applicationIdFilter, Predicate<? super Instance> instanceFilter) {
		Assert.assertTrue(applicationIdFilter.test("129856d2-c53b-4971-b100-4ce371b78070"));
		
		Instance i = new ConfigurationTargetInstance(null, "129856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl", null);
		Assert.assertTrue(instanceFilter.test(i));
		
		return null;
	}

	@Test
	public void testfilterInstanceListPositive() {
		this.getMetrics("129856d2-c53b-4971-b100-4ce371b78070", "42");
	}

}
