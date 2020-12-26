package org.cloudfoundry.promregator.endpoint;

import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryLabel;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryResponse;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
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

	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Autowired
	private DiscoveryEndpoint subject;
	
	@Test
	public void testIssue180() {
		HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
		
		ResponseEntity<DiscoveryResponse[]> responseE = this.subject.getDiscovery(requestMock);
		Assert.assertEquals(HttpStatus.OK, responseE.getStatusCode());
		
		DiscoveryResponse[] response = responseE.getBody();
		
		Assert.assertEquals(1, response.length);
		
		DiscoveryLabel label = response[0].getLabels();
		Assert.assertEquals(null, label.getApplicationId());
		Assert.assertEquals(null, label.getApplicationName());
		Assert.assertEquals(null, label.getInstanceId());
		Assert.assertEquals(null, label.getInstanceNumber());
		Assert.assertEquals(null, label.getOrgName());
		Assert.assertEquals(null, label.getSpaceName());
		Assert.assertEquals(EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS, label.getTargetPath());
	}

}
