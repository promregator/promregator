package org.cloudfoundry.promregator.lifecycle;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.discovery.InstanceKey;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.springframework.jms.annotation.JmsListener;

public class InstanceLifecycleHandler {
	private static final Logger log = Logger.getLogger(InstanceLifecycleHandler.class);
	
	@JmsListener(destination=MessageBusDestination.DISCOVERER_INSTANCE_REMOVED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void receiver(InstanceKey instanceKey) {
		this.deregisterMetricsSamples(instanceKey);
		
	}

	private void deregisterMetricsSamples(InstanceKey instanceKey) {
		log.info(String.format("De-registering metrics samples for instance %s/%s/%s/%s", instanceKey.getOrgName(), instanceKey.getSpaceName(), instanceKey.getAppName(), instanceKey.getInstanceId()));
		
		String orgName = instanceKey.getOrgName();
		String spaceName = instanceKey.getSpaceName();
		String appName = instanceKey.getAppName();
		
		AbstractMetricFamilySamplesEnricher mfse = new CFMetricFamilySamplesEnricher(orgName, spaceName, appName, instanceKey.getInstanceId());
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, true);
		// NB: requestLatency is enabled to allow access to the child, if necessary
		
		mfm.deregisterSamplesFromRegistry();
	}
}
