package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.Target;
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
	
	@Override
	public List<ResolvedTarget> resolveTargets(Target configTarget) {
		
		Mono<String> orgIdMono = this.cfAccessor.retrieveOrgId(configTarget.getOrgName())
				.map( r -> r.getResources())
				.map( l -> l.get(0))
				.map( e -> e.getMetadata()) 
				.map( entry -> entry.getId());
		
		Mono<String> spaceIdMono = orgIdMono.flatMap(orgId -> {
			return this.cfAccessor.retrieveSpaceId(orgId, configTarget.getSpaceName());
		}).map( r -> r.getResources())
			.map( l -> l.get(0))
			.map( e -> e.getMetadata())
			.map( entry -> entry.getId());
		
		Mono<ListApplicationsResponse> responseMono = Mono.zip(orgIdMono, spaceIdMono)
			.flatMap( tuple -> this.cfAccessor.retrieveAllApplicationIdsInSpace(tuple.getT1(), tuple.getT2()));
		
		Flux<String> applicationsInSpace = responseMono.map( r -> r.getResources())
			.flatMapMany(resources -> {
				List<String> appNames = new LinkedList<>();
				for (ApplicationResource ar : resources) {
					if (!isApplicationInScrapableState(ar.getEntity().getState())) {
						continue;
					}
					
					appNames.add(ar.getEntity().getName());
				}
				
				return Flux.fromIterable(appNames);
			});
		
		Iterable<String> applicationNames = applicationsInSpace.toIterable();

		List<ResolvedTarget> resolvedTargets = new LinkedList<>();

		for (String appName : applicationNames) {
			ResolvedTarget newTarget = new ResolvedTarget();
			newTarget.setOrgName(configTarget.getOrgName());
			newTarget.setSpaceName(configTarget.getSpaceName());
			newTarget.setApplicationName(appName);
			newTarget.setPath(configTarget.getPath());
			newTarget.setProtocol(configTarget.getProtocol());
			
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
