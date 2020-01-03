package org.cloudfoundry.promregator.endpoint;

import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryLabel;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryResponse;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="no_promregator_metrics.properties")
public class DiscoveryEndpointNoPromregatorMetricsTest {

	@Autowired
	private DiscoveryEndpoint subject;

	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}
	
	@Test
	public void testStraightForward() {
		HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
		
		ResponseEntity<DiscoveryResponse[]> responseEntity = this.subject.getDiscovery(requestMock);
		DiscoveryResponse[] response = responseEntity.getBody();
		
		Assert.assertEquals(3, response.length);
		
		Assert.assertEquals(1, response[0].getTargets().length);
		Assert.assertEquals("discovery-hostname:1234", response[0].getTargets()[0]);
		
		Assert.assertEquals(1, response[1].getTargets().length);
		Assert.assertEquals("discovery-hostname:1234", response[1].getTargets()[0]);
		
		Assert.assertEquals(1, response[2].getTargets().length);
		Assert.assertEquals("discovery-hostname:1234", response[2].getTargets()[0]);
		
		DiscoveryLabel label = null;
		
		label = response[0].getLabels();
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.getApplicationId());
		Assert.assertEquals("unittestapp", label.getApplicationName());
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:0", label.getInstanceId());
		Assert.assertEquals("0", label.getInstanceNumber());
		Assert.assertEquals("unittestorg", label.getOrgName());
		Assert.assertEquals("unittestspace", label.getSpaceName());
		Assert.assertEquals("/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/0", label.getTargetPath());

		label = response[1].getLabels();
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.getApplicationId());
		Assert.assertEquals("unittestapp", label.getApplicationName());
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:1", label.getInstanceId());
		Assert.assertEquals("1", label.getInstanceNumber());
		Assert.assertEquals("unittestorg", label.getOrgName());
		Assert.assertEquals("unittestspace", label.getSpaceName());
		Assert.assertEquals("/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/1", label.getTargetPath());

		label = response[2].getLabels();
		Assert.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300", label.getApplicationId());
		Assert.assertEquals("unittestapp2", label.getApplicationName());
		Assert.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300:0", label.getInstanceId());
		Assert.assertEquals("0", label.getInstanceNumber());
		Assert.assertEquals("unittestorg", label.getOrgName());
		Assert.assertEquals("unittestspace", label.getSpaceName());
		Assert.assertEquals("/singleTargetMetrics/1142a717-e27d-4028-89d8-b42a0c973300/0", label.getTargetPath());

		// NB: /promregatorMetrics endpoint must not be mentioned here (that's checked with Assert.assertEquals(3, response.length); )
	}

}
