package org.cloudfoundry.promregator.endpoint;

import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryLabel;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;


@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = NoTargetsConfiguredSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class DiscoveryEndpointNoTargetsDefinedTest {

	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Autowired
	private DiscoveryEndpoint subject;
	
	@Test
	public void testIssue180() {
		HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
		
		ResponseEntity<DiscoveryResponse[]> responseE = this.subject.getDiscovery(requestMock);
		Assertions.assertEquals(HttpStatus.OK, responseE.getStatusCode());
		
		DiscoveryResponse[] response = responseE.getBody();
		
		Assertions.assertEquals(1, response.length);
		
		DiscoveryLabel label = response[0].getLabels();
		Assertions.assertEquals(null, label.getApplicationId());
		Assertions.assertEquals(null, label.getApplicationName());
		Assertions.assertEquals(null, label.getInstanceId());
		Assertions.assertEquals(null, label.getInstanceNumber());
		Assertions.assertEquals(null, label.getOrgName());
		Assertions.assertEquals(null, label.getSpaceName());
		Assertions.assertEquals(EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS, label.getTargetPath());
	}

}
