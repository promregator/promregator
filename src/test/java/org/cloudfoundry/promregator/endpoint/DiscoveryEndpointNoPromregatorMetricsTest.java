package org.cloudfoundry.promregator.endpoint;

import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryLabel;
import org.cloudfoundry.promregator.endpoint.DiscoveryEndpoint.DiscoveryResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedMetricsEndpointSpringApplication.class)
@TestPropertySource(locations="no_promregator_metrics.properties")
public class DiscoveryEndpointNoPromregatorMetricsTest {

	@Autowired
	private DiscoveryEndpoint subject;
	
	@Test
	public void testStraightForward() {
		HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
		
		DiscoveryResponse[] response = this.subject.getDiscovery(requestMock);
		
		Assert.assertEquals(3, response.length);
		
		Assert.assertEquals(1, response[0].getTargets().length);
		Assert.assertEquals("discovery-hostname:1234", response[0].getTargets()[0]);
		
		Assert.assertEquals(1, response[1].getTargets().length);
		Assert.assertEquals("discovery-hostname:1234", response[1].getTargets()[0]);
		
		Assert.assertEquals(1, response[2].getTargets().length);
		Assert.assertEquals("discovery-hostname:1234", response[2].getTargets()[0]);
		
		DiscoveryLabel label = null;
		
		label = response[0].getLabels();
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.get__meta_promregator_target_applicationId());
		Assert.assertEquals("unittestapp", label.get__meta_promregator_target_applicationName());
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:0", label.get__meta_promregator_target_instanceId());
		Assert.assertEquals("0", label.get__meta_promregator_target_instanceNumber());
		Assert.assertEquals("unittestorg", label.get__meta_promregator_target_orgName());
		Assert.assertEquals("unittestspace", label.get__meta_promregator_target_spaceName());
		Assert.assertEquals("/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/0", label.get__meta_promregator_target_path());

		label = response[1].getLabels();
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf", label.get__meta_promregator_target_applicationId());
		Assert.assertEquals("unittestapp", label.get__meta_promregator_target_applicationName());
		Assert.assertEquals("faedbb0a-2273-4cb4-a659-bd31331f7daf:1", label.get__meta_promregator_target_instanceId());
		Assert.assertEquals("1", label.get__meta_promregator_target_instanceNumber());
		Assert.assertEquals("unittestorg", label.get__meta_promregator_target_orgName());
		Assert.assertEquals("unittestspace", label.get__meta_promregator_target_spaceName());
		Assert.assertEquals("/singleTargetMetrics/faedbb0a-2273-4cb4-a659-bd31331f7daf/1", label.get__meta_promregator_target_path());

		label = response[2].getLabels();
		Assert.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300", label.get__meta_promregator_target_applicationId());
		Assert.assertEquals("unittestapp2", label.get__meta_promregator_target_applicationName());
		Assert.assertEquals("1142a717-e27d-4028-89d8-b42a0c973300:0", label.get__meta_promregator_target_instanceId());
		Assert.assertEquals("0", label.get__meta_promregator_target_instanceNumber());
		Assert.assertEquals("unittestorg", label.get__meta_promregator_target_orgName());
		Assert.assertEquals("unittestspace", label.get__meta_promregator_target_spaceName());
		Assert.assertEquals("/singleTargetMetrics/1142a717-e27d-4028-89d8-b42a0c973300/0", label.get__meta_promregator_target_path());

		// NB: /promregatorMetrics endpoint must not be mentioned here (that's checked with Assert.assertEquals(3, response.length); )
	}

}
