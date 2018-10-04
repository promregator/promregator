package org.cloudfoundry.promregator.discovery;

import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.Null;

import org.cloudfoundry.promregator.scanner.Instance;

public interface CFDiscoverer {
	
	/**
	 * performs the discovery based on the configured set of targets in the configuration, (pre-)filtering the returned set applying the filter criteria supplied.
	 * The instances discovered are automatically registered at this Discoverer
	 * @param applicationIdFilter the (pre-)filter based on ApplicationIds, allowing to early filter the list of instances to discover
	 * @param instanceFilter the (pre-)filter based on the Instance instance, allowing to filter the lost if instances to discover
	 * @return the list of Instances which were discovered (and registered).
	 */
	List<Instance> discover(@Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter);
}
