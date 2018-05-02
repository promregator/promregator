package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.client.HttpClientErrorException;

public class SingleTargetMetricsEndpointTest extends SingleTargetMetricsEndpoint {

	@Override
	public String handleRequest() {
		// necessary for proper test isolation
		return null;
	}

	@Test
	public void testfilterInstanceListPositive() {
		// NB: required to set up the test properly
		this.getMetrics("129856d2-c53b-4971-b100-4ce371b78070", "42");
		
		List<Instance> instanceList = new LinkedList<>();
		
		ResolvedTarget t;
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("unittestapp");
		t.setPath("/metricsPath");
		t.setProtocol("https");
		
		instanceList.add(new Instance(t, "129856d2-c53b-4971-b100-4ce371b78070:41", "https://someurl"));
		instanceList.add(new Instance(t, "229856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl"));
		
		Instance i;
		i = new Instance(t, "129856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl");
		
		instanceList.add(i);
		
		List<Instance> result = this.filterInstanceList(instanceList);
		
		Assert.assertEquals(1, result.size());
		Assert.assertEquals(i, result.get(0));
	}
	
	
	@Test(expected=HttpClientErrorException.class)
	public void testfilterInstanceListNegative() {
		// NB: required to set up the test properly
		this.getMetrics("59ff5929-b593-4d90-a3b3-95f4883a8553", "1");
		
		List<Instance> instanceList = new LinkedList<>();
		
		ResolvedTarget t;
		t = new ResolvedTarget();
		t.setOrgName("unittestorg");
		t.setSpaceName("unittestspace");
		t.setApplicationName("unittestapp");
		t.setPath("/metricsPath");
		t.setProtocol("https");
		
		instanceList.add(new Instance(t, "129856d2-c53b-4971-b100-4ce371b78070:41", "https://someurl"));
		instanceList.add(new Instance(t, "229856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl"));
		
		Instance i;
		i = new Instance(t, "129856d2-c53b-4971-b100-4ce371b78070:42", "https://someurl");
		
		instanceList.add(i);
		
		List<Instance> result = this.filterInstanceList(instanceList);
	}
	
}
