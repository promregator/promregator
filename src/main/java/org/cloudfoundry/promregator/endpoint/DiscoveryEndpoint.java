package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.scanner.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.annotation.JsonGetter;

@RestController
@RequestMapping(EndpointConstants.ENDPOINT_PATH_DISCOVERY)
@Scope(value=WebApplicationContext.SCOPE_REQUEST)
public class DiscoveryEndpoint {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryEndpoint.class);
	
	@Autowired
	private CFMultiDiscoverer cfDiscoverer;

	@Value("${promregator.discovery.hostname:#{null}}")
	private String myHostname;
	
	@Value("${promregator.discovery.port:0}")
	private int myPort;
	
	@Value("${promregator.discovery.ownMetricsEndpoint:true}")
	private boolean promregatorMetricsEndpoint;
	
	public static class DiscoveryLabel {
		private String targetPath;
		private String orgName;
		private String spaceName;
		private String applicationName;
		private String applicationId;
		private String instanceNumber;
		private String instanceId;

		public DiscoveryLabel(String path) {
			super();
			this.targetPath = path;
		}
		
		public DiscoveryLabel(String path, Instance instance) {
			this(path);
			
			this.orgName = instance.getTarget().getOrgName();
			this.spaceName = instance.getTarget().getSpaceName();
			this.applicationName = instance.getTarget().getApplicationName();
			this.applicationId = instance.getApplicationId();
			this.instanceNumber = instance.getInstanceNumber();
			this.instanceId = instance.getInstanceId();
		}
		
		@JsonGetter("__meta_promregator_target_path")
		public String getTargetPath() {
			return targetPath;
		}

		@JsonGetter("__meta_promregator_target_orgName")
		public String getOrgName() {
			return orgName;
		}

		@JsonGetter("__meta_promregator_target_spaceName")
		public String getSpaceName() {
			return spaceName;
		}

		@JsonGetter("__meta_promregator_target_applicationName")
		public String getApplicationName() {
			return applicationName;
		}

		@JsonGetter("__meta_promregator_target_applicationId")
		public String getApplicationId() {
			return applicationId;
		}

		@JsonGetter("__meta_promregator_target_instanceNumber")
		public String getInstanceNumber() {
			return instanceNumber;
		}

		@JsonGetter("__meta_promregator_target_instanceId")
		public String getInstanceId() {
			return instanceId;
		}
		
		@JsonGetter("__metrics_path__")
		public String getMetricsPath() {
			return this.targetPath;
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
	
	@GetMapping(produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DiscoveryResponse[]> getDiscovery(HttpServletRequest request) {
		
		List<Instance> instances = this.cfDiscoverer.discover(null, null);
		// @SonarQube: No, there shall not be any || instances.isEmpty() here! Why? See https://github.com/promregator/promregator/issues/180
		if (instances == null) {
			return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
		}
		
		String localHostname = this.myHostname != null ? this.myHostname : request.getLocalName();
		int localPort = this.myPort != 0 ? this.myPort : request.getLocalPort();
		final String[] targets = { String.format("%s:%d", localHostname, localPort) };
		
		log.info("Using scraping target {} in discovery response", targets[0]);
		
		List<DiscoveryResponse> result = new LinkedList<>();
		for (Instance instance : instances) {
			
			String path = String.format(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/%s/%s", instance.getApplicationId(), instance.getInstanceNumber());
			DiscoveryLabel dl = new DiscoveryLabel(path, instance);
			
			DiscoveryResponse dr = new DiscoveryResponse(targets, dl);
			result.add(dr);
		}
		
		if (this.promregatorMetricsEndpoint) {
			// finally, also add our own metrics endpoint
			DiscoveryLabel dl = new DiscoveryLabel(EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS);
			result.add(new DiscoveryResponse(targets, dl));
		}
		
		log.info("Returning discovery document with {} targets", result.size());
		
		return new ResponseEntity<>(result.toArray(new DiscoveryResponse[0]), HttpStatus.OK);
	}
}
