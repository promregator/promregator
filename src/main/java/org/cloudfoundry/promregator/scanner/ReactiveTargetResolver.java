package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ReactiveTargetResolver implements TargetResolver {

	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;

	@Autowired
	private CFAccessor cfAccessor;
	
	@Autowired
	private InternalMetrics internalMetrics;
	
	@Override
	public List<ResolvedTarget> resolveTargets(Target configTarget) {
		
		List<Target> resolvedTargets = new LinkedList<>();
		
		String orgName = configTarget.getOrgName();
		String spaceName = configTarget.getSpaceName();
		String key = String.format("%s|%s", orgName, spaceName);
		Flux<String> applicationsInSpace = this.applicationsInSpaceMap.get(key);
		if (applicationsInSpace == null) {
			// cache miss
			
			// for retrieving all applications, we need the orgId and spaceId
			// the names are not sufficient
			Mono<String> orgIdMono = this.getOrgId(orgName);
			Mono<String> spaceIdMono = this.getSpaceId(orgIdMono, spaceName);
			
			Mono<ListApplicationsResponse> responseMono = this.cfAccessor.retrieveAllApplicationIdsInSpace(orgIdMono.block(), spaceIdMono.block());
			
			applicationsInSpace = responseMono.flatMapMany(response -> {
				List<ApplicationResource> resources = response.getResources();
				if (resources == null) {
					return Flux.empty();
				}
				
				List<String> appNames = new LinkedList<>();
				for (ApplicationResource ar : resources) {
					if (!isApplicationInScrapableState(ar.getEntity().getState())) {
						continue;
					}
					
					appNames.add(ar.getEntity().getName());
				}
				
				return Flux.fromIterable(appNames);
			});
			
			this.applicationsInSpaceMap.put(key, applicationsInSpace);
		}
		
		Iterable<String> applicationNames = applicationsInSpace.toIterable();
		
		for (String appName : applicationNames) {
			Target newTarget = new Target();
			newTarget.setOrgName(target.getOrgName());
			newTarget.setSpaceName(target.getSpaceName());
			newTarget.setApplicationName(appName);
			newTarget.setPath(target.getPath());
			newTarget.setProtocol(target.getProtocol());
			
			resolvedTargets.add(newTarget);
		}
		
		return resolvedTargets;
	}

	
	
	private boolean isApplicationInScrapableState(String state) {
		if ("STARTED".equals(state)) {
			return true;
		}
		
		// TODO: To be enhanced, once we know of further states, which are
		// also scrapable.
		
		return false;
	}
}
