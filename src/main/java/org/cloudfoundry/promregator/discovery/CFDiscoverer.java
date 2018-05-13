package org.cloudfoundry.promregator.discovery;

import java.util.List;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.Instance;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.ResolvedTargetManager;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CFDiscoverer {
	private static final Logger log = Logger.getLogger(CFDiscoverer.class);
	
	@Autowired
	private TargetResolver targetResolver;
	
	@Autowired
	private ResolvedTargetManager resolvedTargetManager;
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;
	
	public List<Instance> discover() {
		log.info(String.format("We have %d targets configured", this.promregatorConfiguration.getTargets().size()));
		
		List<ResolvedTarget> resolvedTargets = this.targetResolver.resolveTargets(this.promregatorConfiguration.getTargets());
		log.info(String.format("Raw list contains %d resolved targets", resolvedTargets.size()));
		
		// ensure that the ResolvedTargets are registered / touched properly
		for (ResolvedTarget rt : resolvedTargets) {
			this.resolvedTargetManager.registerResolvedTarget(rt);
		}
		
		List<Instance> instanceList = this.appInstanceScanner.determineInstancesFromTargets(resolvedTargets);
		log.info(String.format("Raw list contains %d instances", instanceList.size()));

		return instanceList;
	}
	
}
