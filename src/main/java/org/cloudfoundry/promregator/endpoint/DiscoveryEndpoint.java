package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.discovery.CFDiscoverer;
import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@RestController
@RequestMapping(EndpointConstants.ENDPOINT_PATH_DISCOVERY)
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
public class DiscoveryEndpoint {

	private static final Logger log = Logger.getLogger(DiscoveryEndpoint.class);
	
	@Autowired
	private CFDiscoverer cfDiscoverer;

	@Value("${promregator.discovery.hostname:#{null}}")
	private String myHostname;
	
	@Value("${promregator.discovery.port:0}")
	private int myPort;
	
	public static class DiscoveryLabel {
		private String __meta_promregator_target_path;
		private String __meta_promregator_target_orgName;
		private String __meta_promregator_target_spaceName;
		private String __meta_promregator_target_applicationName;
		private String __meta_promregator_target_applicationId;
		private String __meta_promregator_target_instanceNumber;
		private String __meta_promregator_target_instanceId;

		public DiscoveryLabel(String __meta_promregator_target_path) {
			super();
			this.__meta_promregator_target_path = __meta_promregator_target_path;
		}
		
		public DiscoveryLabel(String __meta_promregator_target_path, Instance instance) {
			this(__meta_promregator_target_path);
			
			this.__meta_promregator_target_orgName = instance.getTarget().getOrgName();
			this.__meta_promregator_target_spaceName = instance.getTarget().getSpaceName();
			this.__meta_promregator_target_applicationName = instance.getTarget().getApplicationName();
			this.__meta_promregator_target_applicationId = instance.getApplicationId();
			this.__meta_promregator_target_instanceNumber = instance.getInstanceNumber();
			this.__meta_promregator_target_instanceId = instance.getInstanceId();
		}
		
		public String get__meta_promregator_target_path() {
			return __meta_promregator_target_path;
		}

		public String get__meta_promregator_target_orgName() {
			return __meta_promregator_target_orgName;
		}

		public String get__meta_promregator_target_spaceName() {
			return __meta_promregator_target_spaceName;
		}

		public String get__meta_promregator_target_applicationName() {
			return __meta_promregator_target_applicationName;
		}

		public String get__meta_promregator_target_applicationId() {
			return __meta_promregator_target_applicationId;
		}

		public String get__meta_promregator_target_instanceNumber() {
			return __meta_promregator_target_instanceNumber;
		}

		public String get__meta_promregator_target_instanceId() {
			return __meta_promregator_target_instanceId;
		}
	}
	
	public static class DiscoveryResponse {
		private String[] targets;
		
		private DiscoveryLabel labels;

		public DiscoveryResponse(String[] targets, DiscoveryLabel labels) {
			super();
			this.targets = targets.clone();
			this.labels = labels;
		}

		public String[] getTargets() {
			return targets.clone();
		}

		public DiscoveryLabel getLabels() {
			return labels;
		}
	}
	
	@RequestMapping(method = RequestMethod.GET, produces=MediaType.APPLICATION_JSON_UTF8_VALUE)
	public DiscoveryResponse[] getDiscovery(HttpServletRequest request) {
		
		List<Instance> instances = this.cfDiscoverer.discover(null, null);
		
		String localHostname = this.myHostname != null ? this.myHostname : request.getLocalName();
		int localPort = this.myPort != 0 ? this.myPort : request.getLocalPort();
		final String[] targets = { String.format("%s:%d", localHostname, localPort) };
		
		log.info(String.format("Using scraping target %s in discovery response", targets[0]));
		
		List<DiscoveryResponse> result = new LinkedList<>();
		for (Instance instance : instances) {
			
			String path = String.format(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/%s/%s", instance.getApplicationId(), instance.getInstanceNumber());
			DiscoveryLabel dl = new DiscoveryLabel(path, instance);
			
			DiscoveryResponse dr = new DiscoveryResponse(targets, dl);
			result.add(dr);
		}
		
		// finally, also add our own metrics endpoint
		DiscoveryLabel dl = new DiscoveryLabel(EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS);
		result.add(new DiscoveryResponse(targets, dl));
		
		log.info(String.format("Returing discovery document with %d targets", result.size()));
		
		return result.toArray(new DiscoveryResponse[0]);
	}
}
