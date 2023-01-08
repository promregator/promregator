package org.cloudfoundry.promregator.lifecycle;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.cloudfoundry.promregator.fetcher.MetricsFetcherMetrics;
import org.cloudfoundry.promregator.messagebus.MessageBus;
import org.cloudfoundry.promregator.messagebus.MessageBusTopic;
import org.cloudfoundry.promregator.messagebus.MessageSubscriber;
import org.cloudfoundry.promregator.rewrite.AbstractMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.rewrite.CFAllLabelsMetricFamilySamplesEnricher;
import org.cloudfoundry.promregator.scanner.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class InstanceLifecycleHandler implements MessageSubscriber {
	private static final Logger log = LoggerFactory.getLogger(InstanceLifecycleHandler.class);
	
	@Autowired
	private MessageBus messageBus;
	
	@PostConstruct
	public void registerAtMessageBus() {
		this.messageBus.subscribe(MessageBusTopic.DISCOVERER_INSTANCE_REMOVED, this);
	}
	
	@Override
	public void receiveMessage(String topic, Object message) {
		Instance instance = (Instance) message;
		
		this.deregisterMetricsSamples(instance);
		
	}

	private void deregisterMetricsSamples(Instance instance) {
		log.info("De-registering metrics samples for instance {}", instance);
		
		String orgName = instance.getTarget().getOrgName();
		String spaceName = instance.getTarget().getSpaceName();
		String appName = instance.getTarget().getApplicationName();
		
		AbstractMetricFamilySamplesEnricher mfse = new CFAllLabelsMetricFamilySamplesEnricher(orgName, spaceName, appName, instance.getInstanceId());
		List<String> labelValues = mfse.getEnrichedLabelValues(new LinkedList<>());
		String[] ownTelemetryLabelValues = labelValues.toArray(new String[0]);
		
		MetricsFetcherMetrics mfm = new MetricsFetcherMetrics(ownTelemetryLabelValues, true);
		// NB: requestLatency is enabled to allow access to the child, if necessary
		
		mfm.deregisterSamplesFromRegistry();
	}

}
