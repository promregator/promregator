package org.cloudfoundry.promregator.endpoint;

import java.util.function.Predicate;

import org.cloudfoundry.promregator.scanner.Instance;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

public class SingleTargetMetricsEndpointInternalTestPositive extends SingleTargetMetricsEndpoint {

	@Override
	public String handleRequest(Predicate<? super String> applicationIdFilter, Predicate<? super Instance> instanceFilter) {
		Assert.assertTrue(applicationIdFilter.test("129856d2-c53b-4971-b100-4ce371b78070"));
		
		Instance i = new Instance(null, "129856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl");
		Assert.assertTrue(instanceFilter.test(i));
		
		return null;
	}

	@Test
	public void testfilterInstanceListPositive() {
		ResponseEntity<String> result = this.getMetrics("129856d2-c53b-4971-b100-4ce371b78070", "42");  // real test: no exception is raised
		Assert.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}

}
