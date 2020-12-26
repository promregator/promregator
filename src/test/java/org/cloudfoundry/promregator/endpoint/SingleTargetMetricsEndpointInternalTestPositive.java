package org.cloudfoundry.promregator.endpoint;

import java.util.function.Predicate;

import org.cloudfoundry.promregator.scanner.Instance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class SingleTargetMetricsEndpointInternalTestPositive extends SingleTargetMetricsEndpoint {

	@Override
	public String handleRequest(Predicate<? super String> applicationIdFilter, Predicate<? super Instance> instanceFilter) {
		Assertions.assertTrue(applicationIdFilter.test("129856d2-c53b-4971-b100-4ce371b78070"));
		
		Instance i = new Instance(null, "129856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl");
		Assertions.assertTrue(instanceFilter.test(i));
		
		return null;
	}

	@Test
	public void testfilterInstanceListPositive() {
		ResponseEntity<String> result = this.getMetrics("129856d2-c53b-4971-b100-4ce371b78070", "42");  // real test: no exception is raised
		Assertions.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}

}
