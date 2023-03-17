package org.cloudfoundry.promregator.endpoint;

import jakarta.servlet.http.HttpServletRequest;

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
@TestPropertySource(locations="no_promregator_metrics.properties")
public class DiscoveryEndpointNoPromregatorMetricsTest {

	@Autowired
	private DiscoveryEndpoint subject;

	@AfterAll
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	void testStraightForward() {
		HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
		
		ResponseEntity<DiscoveryResponse[]> responseEntity = this.subject.getDiscovery(requestMock);
		DiscoveryResponse[] response = responseEntity.getBody();
		
		Assertions.assertEquals(3, response.length);
		
		Assertions.assertEquals(1, response[0].getTargets().length);
		Assertions.assertEquals("discovery-hostname:1234", response[0].getTargets()[0]);
		
		Assertions.assertEquals(1, response[1].getTargets().length);
		Assertions.assertEquals("discovery-hostname:1234", response[1].getTargets()[0]);
		
		Assertions.assertEquals(1, response[2].getTargets().length);
		Assertions.assertEquals("discovery-hostname:1234", response[2].getTargets()[0]);
		
		DiscoveryLabel label = null;
		
		label = response[0].getLabels();
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.getApplicationId());
		Assertions.assertEquals("unittestapp", label.getApplicationName());
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:0", label.getInstanceId());
		Assertions.assertEquals("0", label.getInstanceNumber());
		Assertions.assertEquals("unittestorg", label.getOrgName());
		Assertions.assertEquals("unittestspace", label.getSpaceName());
		Assertions.assertEquals("/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/0", label.getTargetPath());

		label = response[1].getLabels();
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.getApplicationId());
		Assertions.assertEquals("unittestapp", label.getApplicationName());
		Assertions.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:1", label.getInstanceId());
		Assertions.assertEquals("1", label.getInstanceNumber());
		Assertions.assertEquals("unittestorg", label.getOrgName());
		Assertions.assertEquals("unittestspace", label.getSpaceName());
		Assertions.assertEquals("/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/1", label.getTargetPath());

		label = response[2].getLabels();
		Assertions.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300", label.getApplicationId());
		Assertions.assertEquals("unittestapp2", label.getApplicationName());
		Assertions.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300:0", label.getInstanceId());
		Assertions.assertEquals("0", label.getInstanceNumber());
		Assertions.assertEquals("unittestorg", label.getOrgName());
		Assertions.assertEquals("unittestspace", label.getSpaceName());
		Assertions.assertEquals("/singleTargetMetrics/1142a717-e27d-4028-89d8-b42a0c973300/0", label.getTargetPath());

		// NB: /promregatorMetrics endpoint must not be mentioned here (that's checked with Assertions.assertEquals(3, response.length); )
	}

}
