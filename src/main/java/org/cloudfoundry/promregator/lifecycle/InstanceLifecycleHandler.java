package org.cloudfoundry.promregator.lifecycle;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.springframework.jms.annotation.JmsListener;

public class InstanceLifecycleHandler {
	private static final Logger log = Logger.getLogger(InstanceLifecycleHandler.class);
	
	@JmsListener(destination=MessageBusDestination.DISCOVERER_INSTANCE_REMOVED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void receiver(Instance instance) {
		this.deregisterMetricsSamples(instance);
		
	}

	private void deregisterMetricsSamples(Instance instance) {
		log.info(String.format("De-registering metrics samples for instance %s", instance));
		
		String orgName = instance.getTarget().getOrgName();
		String spaceName = instance.getTarget().getSpaceName();
		String appName = instance.getTarget().getApplicationName();
		
		AbstractMetricFamilySamplesEnricher mfse = new CFMetricFamilySamplesEnricher(orgName, spaceName, appName, instance.getInstanceId());
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, true);
		// NB: requestLatency is enabled to allow access to the child, if necessary
		
		mfm.deregisterSamplesFromRegistry();
	}
}
