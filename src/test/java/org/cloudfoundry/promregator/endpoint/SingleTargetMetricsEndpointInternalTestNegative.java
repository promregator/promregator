package org.cloudfoundry.promregator.endpoint;

import java.util.function.Predicate;

import org.cloudfoundry.promregator.scanner.Instance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SingleTargetMetricsEndpointInternalTestNegative extends SingleTargetMetricsEndpoint {
	@Override
	public String handleRequest(Predicate<? super String> applicationIdFilter, Predicate<? super Instance> instanceFilter) {
		Assertions.assertFalse(applicationIdFilter.test("229856d2-c53b-4971-b100-4ce371b78070"));
		
		Instance i = new Instance(null, "129856d2-c53b-4971-b100-4ce371b78070:41", "https://someurl");
		Assertions.assertFalse(instanceFilter.test(i));

		i = new Instance(null, "229856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl");
		Assertions.assertFalse(instanceFilter.test(i));
		
		return null;
	}

	@Test
	void testfilterInstanceListPositive() {
		ResponseEntity<String> result = this.getMetrics("129856d2-c53b-4971-b100-4ce371b78070", "42"); // real test: no exception is raised
		Assertions.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}
	
}
