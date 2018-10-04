package org.cloudfoundry.promregator.discovery;

import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.Null;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.springframework.beans.factory.annotation.Autowired;

public class ConfigurationTargetCFDiscoverer implements CFDiscoverer {
	private static final Logger log = Logger.getLogger(ConfigurationTargetCFDiscoverer.class);
	
	@Autowired
	private TargetResolver targetResolver;
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;
	
	@Override
	public List<Instance> discover(@Null Predicate<? super String> applicationIdFilter,
			@Null Predicate<? super Instance> instanceFilter) {
		
		log.debug(String.format("We have %d targets configured", this.promregatorConfiguration.getTargets().size()));
		
		List<ResolvedTarget> resolvedTargets = this.targetResolver.resolveTargets(this.promregatorConfiguration.getTargets());
		if (resolvedTargets == null) {
			log.warn("Target resolved was unable to resolve configured targets");
			return null;
		}
		log.debug(String.format("Raw list contains %d resolved targets", resolvedTargets.size()));
		
		List<Instance> instanceList = this.appInstanceScanner.determineInstancesFromTargets(resolvedTargets, applicationIdFilter, instanceFilter);
		if (instanceList == null) {
			log.warn("Instance Scanner unable to determine instances from provided targets");
			return null;
		}
		log.debug(String.format("Raw list contains %d instances", instanceList.size()));
		return instanceList;

	}

}
