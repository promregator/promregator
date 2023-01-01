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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;


@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class DiscoveryEndpointTest {

	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Autowired
	private DiscoveryEndpoint subject;
	
	@Test
	void testStraightForward() {
		HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
		
		ResponseEntity<DiscoveryResponse[]> responseE = this.subject.getDiscovery(requestMock);
		DiscoveryResponse[] response = responseE.getBody();
		
		Assertions.assertEquals(4, response.length);
		
		Assertions.assertEquals(1, response[0].getTargets().length);
		Assertions.assertEquals("discovery-hostname:1234", response[0].getTargets()[0]);
		
		Assertions.assertEquals(1, response[1].getTargets().length);
		Assertions.assertEquals("discovery-hostname:1234", response[1].getTargets()[0]);
		
		Assertions.assertEquals(1, response[2].getTargets().length);
		Assertions.assertEquals("discovery-hostname:1234", response[2].getTargets()[0]);
		
		Assertions.assertEquals(1, response[3].getTargets().length);
		Assertions.assertEquals("discovery-hostname:1234", response[3].getTargets()[0]);
		
		DiscoveryLabel label = null;
		
		label = response[0].getLabels();
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.getApplicationId());
		Assertions.assertEquals("unittestapp", label.getApplicationName());
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:0", label.getInstanceId());
		Assertions.assertEquals("0", label.getInstanceNumber());
		Assertions.assertEquals("unittestorg", label.getOrgName());
		Assertions.assertEquals("unittestspace", label.getSpaceName());
		Assertions.assertEquals(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/faedbb0a-2273-4cb4-a659-bd31331f7daf/0", label.getTargetPath());

		label = response[1].getLabels();
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.getApplicationId());
		Assertions.assertEquals("unittestapp", label.getApplicationName());
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:1", label.getInstanceId());
		Assertions.assertEquals("1", label.getInstanceNumber());
		Assertions.assertEquals("unittestorg", label.getOrgName());
		Assertions.assertEquals("unittestspace", label.getSpaceName());
		Assertions.assertEquals(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/faedbb0a-2273-4cb4-a659-bd31331f7daf/1", label.getTargetPath());

		label = response[2].getLabels();
		Assertions.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300", label.getApplicationId());
		Assertions.assertEquals("unittestapp2", label.getApplicationName());
		Assertions.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300:0", label.getInstanceId());
		Assertions.assertEquals("0", label.getInstanceNumber());
		Assertions.assertEquals("unittestorg", label.getOrgName());
		Assertions.assertEquals("unittestspace", label.getSpaceName());
		Assertions.assertEquals(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/1142a717-e27d-4028-89d8-b42a0c973300/0", label.getTargetPath());

		label = response[3].getLabels();
		Assertions.assertEquals(null, label.getApplicationId());
		Assertions.assertEquals(null, label.getApplicationName());
		Assertions.assertEquals(null, label.getInstanceId());
		Assertions.assertEquals(null, label.getInstanceNumber());
		Assertions.assertEquals(null, label.getOrgName());
		Assertions.assertEquals(null, label.getSpaceName());
		Assertions.assertEquals(EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS, label.getTargetPath());
		
	}

}
