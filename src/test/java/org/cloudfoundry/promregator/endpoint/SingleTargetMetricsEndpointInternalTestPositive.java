package org.cloudfoundry.promregator.endpoint;

import org.cloudfoundry.promregator.fetcher.FetchResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import io.prometheus.client.exporter.common.TextFormat;

public class SingleTargetMetricsEndpointInternalTestPositive extends SingleTargetMetricsEndpoint {

	@Override
	public FetchResult handleRequest(String applicationId, String instanceId) {
		Assertions.assertEquals("129856d2-c53b-4971-b100-4ce371b78070", applicationId);
		Assertions.assertEquals("129856d2-c53b-4971-b100-4ce371b78070:42", instanceId);
		
		return new FetchResult("", TextFormat.CONTENT_TYPE_OPENMETRICS_100);
	}

	@Test
	void testfilterInstanceListPositive() {
		ResponseEntity<String> result = this.getMetrics("129856d2-c53b-4971-b100-4ce371b78070", "42");  // real test: no exception is raised
		Assertions.assertNotNull(result); // trivial assertion to ensure that unit test is providing an assertion
	}

}
