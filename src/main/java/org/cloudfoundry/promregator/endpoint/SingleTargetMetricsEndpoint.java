package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.scanner.Instance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import io.prometheus.client.exporter.common.TextFormat;

@RestController
@RequestMapping(SingleTargetMetricsEndpoint.ENDPOINT_PATH+"/{applicationId}/{instanceNumber}")
public class SingleTargetMetricsEndpoint extends AbstractMetricsEndpoint {
	public static final String ENDPOINT_PATH = "/singleTargetMetrics";
	
	private String applicationId;
	private String instanceNumber;
	
	@RequestMapping(method = RequestMethod.GET, produces=TextFormat.CONTENT_TYPE_004)
	public String getMetrics(
			@PathVariable String applicationId, 
			@PathVariable String instanceNumber
			) {
		
		this.applicationId = applicationId;
		this.instanceNumber = instanceNumber;
		
		return this.handleRequest();
	}

	@Override
	protected boolean isIncludeGlobalMetrics() {
		// NB: This is done by PromregatorMetricsEndpoint in this scenario instead.
		return false;
	}

	@Override
	protected List<Instance> filterInstanceList(List<Instance> instanceList) {
		String instanceId = String.format("%s:%s", applicationId, instanceNumber);

		Instance selectedInstance = null;
		for (Instance instance : instanceList) {
			if (instance.getInstanceId().equals(instanceId)) {
				selectedInstance = instance;
				break;
			}
		}
		
		if (selectedInstance == null) {
			throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
		}

		List<Instance> response = new LinkedList<Instance>();
		response.add(selectedInstance);
		
		return response;
	}
}
