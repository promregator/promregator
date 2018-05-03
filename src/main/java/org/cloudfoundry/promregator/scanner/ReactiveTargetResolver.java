package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ReactiveTargetResolver implements TargetResolver {

	@Autowired
	private CFAccessor cfAccessor;
	
	@Override
	public List<ResolvedTarget> resolveTargets(List<Target> configTargets) {
		
		Flux<ResolvedTarget> resultFlux = Flux.fromIterable(configTargets)
			// TODO add here a .parallel()
			.flatMap(configTarget -> this.resolveSingleTarget(configTarget));
		
		return resultFlux.collectList().block();
	}
	
	public Flux<ResolvedTarget> resolveSingleTarget(Target configTarget) {
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
		
		Flux<ResolvedTarget> result = applicationsInSpace
			.map(appName -> {
				ResolvedTarget newTarget = new ResolvedTarget();
				
				newTarget.setOrgName(configTarget.getOrgName());
				newTarget.setSpaceName(configTarget.getSpaceName());
				newTarget.setApplicationName(appName);
				newTarget.setPath(configTarget.getPath());
				newTarget.setProtocol(configTarget.getProtocol());
				newTarget.setOriginalTarget(configTarget);
				
				return newTarget;
			});
		
		return result;
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
