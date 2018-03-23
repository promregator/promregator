package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@RestController
@RequestMapping("/discovery")
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
public class DiscoveryEndpoint {

	private static final Logger log = Logger.getLogger(DiscoveryEndpoint.class);
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;

	@Autowired
	private PromregatorConfiguration promregatorConfiguration;

	public class DiscoveryLabel {
		private String __meta_promregator_target_path;

		public DiscoveryLabel(String __meta_promregator_target_path) {
			super();
			this.__meta_promregator_target_path = __meta_promregator_target_path;
		}
		
		public String get__meta_promregator_target_path() {
			return __meta_promregator_target_path;
		}
	}
	
	public class DiscoveryResponse {
		private String[] targets;
		
		private DiscoveryLabel labels;

		public DiscoveryResponse(String[] targets, DiscoveryLabel labels) {
			super();
			this.targets = targets;
			this.labels = labels;
		}

		public String[] getTargets() {
			return targets;
		}

		public DiscoveryLabel getLabels() {
			return labels;
		}
	}
	
	@RequestMapping(method = RequestMethod.GET, produces=MediaType.APPLICATION_JSON_UTF8_VALUE)
	public DiscoveryResponse[] getDiscovery(HttpServletRequest request) {
		List<Instance> instances = this.appInstanceScanner.determineInstancesFromTargets(this.promregatorConfiguration.getTargets());
		
		String localHostname = request.getLocalName();
		int localPort = request.getLocalPort();
		final String[] targets = { String.format("%s:%d", localHostname, localPort) };
		
		List<DiscoveryResponse> result = new LinkedList<>();
		for (Instance instance : instances) {
			
			String path = String.format("/singleTargetMetrics/%s/%s", instance.getApplicationId(), instance.getInstanceNumber());
			DiscoveryLabel dl = new DiscoveryLabel(path);
			
			DiscoveryResponse dr = new DiscoveryResponse(targets, dl);
			result.add(dr);
		}
		
		return result.toArray(new DiscoveryResponse[0]);
	}
}
