package org.cloudfoundry.promregator.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveAppInstanceScanner implements AppInstanceScanner {
	
	private static final Logger log = Logger.getLogger(ReactiveAppInstanceScanner.class);

	private PassiveExpiringMap<String, Mono<String>> hostnameMap;
	private PassiveExpiringMap<String, Mono<String>> domainMap;

	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;


	@PostConstruct
	public void setupMaps() {
		this.hostnameMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.domainMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
	}
	
	private static class OSAVector {
		public ResolvedTarget target;
		
		public String orgId;
		public String spaceId;
		public String applicationId;
		
		public String accessURL;
		public int numberOfInstances;
	}
	
	@Autowired
	private CFAccessor cfAccessor;
	
	@Autowired
	private InternalMetrics internalMetrics;
	
	@Override
	public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets) {
		Flux<ResolvedTarget> targetsFlux = Flux.fromIterable(targets);
		
		Flux<OSAVector> initialOSAVectorFlux = targetsFlux.map(target -> {
			OSAVector v = new OSAVector();
			v.target = target;
			
			return v;
		});
		
		Flux<String> orgIdFlux = initialOSAVectorFlux.flatMap(v -> this.getOrgId(v.target.getOrgName()));
		Flux<OSAVector> OSAVectorOrgFlux = Flux.zip(initialOSAVectorFlux, orgIdFlux).map(tuple -> {
			OSAVector v = tuple.getT1();
			v.orgId = tuple.getT2();
			return v;
		});
		
		Flux<String> spaceIdFlux = OSAVectorOrgFlux.flatMap(v -> this.getSpaceId(v.orgId, v.target.getSpaceName()));
		Flux<OSAVector> OSAVectorSpaceFlux = Flux.zip(OSAVectorOrgFlux, spaceIdFlux).map(tuple -> {
			OSAVector v = tuple.getT1();
			v.spaceId = tuple.getT2();
			return v;
		});
		
		Flux<String> applicationIdFlux = OSAVectorSpaceFlux.flatMap(v -> this.getApplicationId(v.orgId, v.spaceId, v.target.getApplicationName()));
		Flux<OSAVector> OSAVectorApplicationFlux = Flux.zip(OSAVectorSpaceFlux, applicationIdFlux).map(tuple -> {
			OSAVector v = tuple.getT1();
			v.applicationId = tuple.getT2();
			return v;
		});
		
		Flux<String> applicationURLFlux = OSAVectorApplicationFlux.flatMap(v -> this.getApplicationUrl(v.applicationId, v.target.getProtocol()));
		Flux<OSAVector> OSAVectorURLFlux = Flux.zip(OSAVectorApplicationFlux, applicationURLFlux).map(tuple -> {
			OSAVector v = tuple.getT1();
			v.accessURL = this.determineAccessURL(tuple.getT2(), v.target.getPath());
			return v;
		});
		
		Flux<Integer> numberOfInstancesFlux = OSAVectorApplicationFlux.flatMap(v -> this.getNumberOfProcesses(v));
		
		Flux<OSAVector> OSAVectorCompleteFlux = Flux.zip(OSAVectorURLFlux, numberOfInstancesFlux).map(tuple -> { 
			OSAVector v = tuple.getT1();
			v.numberOfInstances = tuple.getT2();
			return v;
		});
		
		Flux<Instance> instancesFlux = OSAVectorCompleteFlux.flatMap(v -> {
			List<Instance> instances = new ArrayList<>(v.numberOfInstances);
			for (int i = 0; i<v.numberOfInstances; i++) {
				Instance inst = new Instance(v.target, String.format("%s:%d", v.applicationId, i), v.accessURL);
				instances.add(inst);
			}
			
			return Flux.fromIterable(instances);
		});
		
		return instancesFlux.collectList().block();
	}
	
	private Mono<String> getOrgId(String orgNameString) {
		Mono<String> orgId = this.cfAccessor.retrieveOrgId(orgNameString).flatMap(response -> {
			List<OrganizationResource> resources = response.getResources();
			if (resources == null) {
				return Mono.empty();
			}
			
			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting org %s", orgNameString));
				return Mono.empty();
			}
			
			OrganizationResource organizationResource = resources.get(0);
			return Mono.just(organizationResource.getMetadata().getId());
		}).cache();
		
		return orgId;
	}

	private Mono<String> getSpaceId(String orgIdString, String spaceNameString) {

		Mono<ListSpacesResponse> listSpacesResponse = this.cfAccessor.retrieveSpaceId(orgIdString, spaceNameString);

		Mono<String> spaceId = listSpacesResponse.flatMap(response -> {
			List<SpaceResource> resources = response.getResources();
			if (resources == null) {
				return Mono.empty();
			}
			
			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting space %s", spaceNameString));
				return Mono.empty();
			}
			
			SpaceResource spaceResource = resources.get(0);
			return Mono.just(spaceResource.getMetadata().getId());
		}).cache();
		
		return spaceId;
	}
	
	private Mono<String> getApplicationId(String orgIdString, String spaceIdString, String applicationNameString) {
		Mono<String> applicationId = this.cfAccessor.retrieveApplicationId(orgIdString, spaceIdString, applicationNameString)
			.flatMap(response -> {
				List<ApplicationResource> resources = response.getResources();
				if (resources == null) {
					return Mono.empty();
				}
				
				if (resources.isEmpty()) {
					log.warn(String.format("Received empty result on requesting application %s", applicationNameString));
					return Mono.empty();
				}
				
				ApplicationResource applicationResource = resources.get(0);
				return Mono.just(applicationResource.getMetadata().getId());
			})
			.cache();
			
		return applicationId;
	}
	
	private Mono<String> getApplicationUrl(String applicationId, String protocol) {
		String key = applicationId;

		Mono<RouteEntity> routeMono = this.cfAccessor.retrieveRouteMapping(applicationId)
			.map(rm -> rm.getResources())
			.map(l -> l.get(0))
			.map(e -> e.getEntity())
			.map(entity -> entity.getRouteId())
			.flatMap(routeId -> { 
				return this.cfAccessor.retrieveRoute(routeId); 
			})
			.map(routeResponse -> routeResponse.getEntity());
		// WARNING! routeResponse.getApplicationsUrl() is the URL back to the application
		// and not the URL which points to the endpoint of the cell!

		Mono<String> domainMono = routeMono.map(route -> route.getDomainId())
			.flatMap(domainId -> {
				return this.getDomain(domainId);
			});
		
		Mono<String> applicationUrlMono = Mono.zip(domainMono, routeMono)
			.map(tuple -> {
				String domain = tuple.getT1();
				RouteEntity route = tuple.getT2();
				
				String url = String.format("%s://%s.%s", protocol, route.getHost(), domain);
				if (route.getPath() != null) {
					url += "/"+route.getPath();
				}
				
				return url;
			});
		
		this.hostnameMap.put(key, applicationUrlMono);
		
		return applicationUrlMono;
	}
	
	private Mono<String> getDomain(String domainIdString) {
		String key = domainIdString;
		
		synchronized (key.intern()) {
			Mono<String> cached = this.domainMap.get(key);
			if (cached != null) {
				this.internalMetrics.countHit("appinstancescanner.domain");
				return cached;
			}
	
			this.internalMetrics.countMiss("appinstancescanner.domain");
	
			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "domain");
	
			cached = Mono.just(domainIdString)
				// start the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().start();
					return tuple.getT1();
				})
				.flatMap(domainId -> {
					return this.cfAccessor.retrieveSharedDomain(domainId);
				}).map(response -> response.getEntity())
				.map(entity -> entity.getName())
				// stop the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().stop();
					return tuple.getT1();
				})
				.cache();
			
			this.domainMap.put(key, cached);
			return cached;
		}
	}
	
	private Mono<Integer> getNumberOfProcesses(OSAVector osav) {
		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "instances");
		
		Mono<ListProcessesResponse> processesResponse = Mono.just(osav)
			// start the timer
			.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
				tuple.getT2().start();
				return tuple.getT1();
			})
			.flatMap(v -> {
				return this.cfAccessor.retrieveProcesses(v.orgId, v.spaceId, v.applicationId);
			})
			// stop the timer
			.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
				tuple.getT2().stop();
				return tuple.getT1();
			});
		
		Mono<Integer> instancesMono = processesResponse.map(pr -> pr.getResources())
			.map(list -> list.get(0))
			.map(e -> e.getInstances());
		
		return instancesMono;
	}
	
	private String determineAccessURL(final String applicationUrl, final String path) {
		String applUrl = applicationUrl;
		if (!applicationUrl.endsWith("/")) {
			applUrl += '/';
		}
		
		String internalPath = path;
		while (internalPath.startsWith("/")) {
			internalPath = internalPath.substring(1);
		}
		
		return applUrl + internalPath;
	}
}
