package org.cloudfoundry.promregator.endpoint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class SingleTargetMetricsEndpointInternalTestPositive extends SingleTargetMetricsEndpoint {

	@Override
	public String handleRequest(String applicationId, String instanceId) {
		Assertions.assertEquals("129856d2-c53b-4971-b100-4ce371b78070", applicationId);
		Assertions.assertEquals("129856d2-c53b-4971-b100-4ce371b78070:42", instanceId);
		
		return null;
	}

	@Test
	void testfilterInstanceListPositive() {
		ResponseEntity<String> result = this.getMetrics("129856d2-c53b-4971-b100-4ce371b78070", "42");  // real test: no exception is raised
		Assertions.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}

}
