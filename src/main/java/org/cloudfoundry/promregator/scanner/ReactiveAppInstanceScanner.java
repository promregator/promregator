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
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveAppInstanceScanner implements AppInstanceScanner {
	
	private static final Logger log = Logger.getLogger(ReactiveAppInstanceScanner.class);

	private PassiveExpiringMap<String, Mono<String>> orgMap;
	private PassiveExpiringMap<String, Mono<String>> spaceMap;
	private PassiveExpiringMap<String, Mono<String>> applicationMap;
	private PassiveExpiringMap<String, Mono<String>> hostnameMap;
	private PassiveExpiringMap<String, Mono<String>> domainMap;

	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;

	@Value("${cf.cache.timeout.space:3600}")
	private int timeoutCacheSpaceLevel;

	@Value("${cf.cache.timeout.org:3600}")
	private int timeoutCacheOrgLevel;

	@PostConstruct
	public void setupMaps() {
		this.orgMap = new PassiveExpiringMap<>(this.timeoutCacheOrgLevel, TimeUnit.SECONDS);
		this.spaceMap = new PassiveExpiringMap<>(this.timeoutCacheSpaceLevel, TimeUnit.SECONDS);
		/*
		 * NB: There is little point in separating the timeouts between applicationMap
		 * and hostnameMap:
		 * - changes to routes may come easily and thus need to be detected fast
		 * - apps can start and stop, we need to see this, too
		 * - instances can be added to apps
		 * - Blue/green deployment may alter both of them
		 * 
		 * In short: both are very volatile and we need to query them often
		 */
		this.applicationMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.hostnameMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.domainMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
	}
	
	private static class OSAVector {
		public Target target;
		
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
	public List<Instance> determineInstancesFromTargets(List<Target> targets) {
		Flux<Target> targetsFlux = Flux.fromIterable(targets);
		
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
		Mono<String> cached = this.orgMap.get(orgNameString);
		if (cached != null) {
			this.internalMetrics.countHit("appinstancescanner.org");
			return cached;
		}
		this.internalMetrics.countMiss("appinstancescanner.org");
		
		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "org");
		
		cached = Mono.just(orgNameString)
			// start the timer
			.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
				tuple.getT2().start();
				return tuple.getT1();
			})
			.flatMap(orgName -> {
				return this.cfAccessor.retrieveOrgId(orgName);
			}).flatMap(response -> {
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
			})
			// stop the timer
			.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
				tuple.getT2().stop();
				return tuple.getT1();
			})
			.cache();
		
		this.orgMap.put(orgNameString, cached);
		return cached;
	}

	private Mono<String> getSpaceId(String orgIdString, String spaceNameString) {
		String key = String.format("%s|%s", orgIdString, spaceNameString);
		
		synchronized(key.intern()) {
			Mono<String> cached = this.spaceMap.get(key);
			if (cached != null) {
				this.internalMetrics.countHit("appinstancescanner.space");
				return cached;
			}
			
			this.internalMetrics.countMiss("appinstancescanner.space");
			
			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "space");
			
			cached = Mono.zip(Mono.just(orgIdString), Mono.just(spaceNameString))
				// start the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().start();
					return tuple.getT1();
				})
				.flatMap(tuple -> {
					return this.cfAccessor.retrieveSpaceId(tuple.getT1(), tuple.getT2());
				}).flatMap(response -> {
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
				})
				// stop the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().stop();
					return tuple.getT1();
				}).cache();
			
			this.spaceMap.put(key, cached);
			return cached;
		}
	}
	
	private Mono<String> getApplicationId(String orgIdString, String spaceIdString, String applicationNameString) {
		String key = String.format("%s|%s|%s", orgIdString, spaceIdString, applicationNameString);
		synchronized(key.intern()) {
			Mono<String> cached = this.applicationMap.get(key);
			if (cached != null) {
				this.internalMetrics.countHit("appinstancescanner.app");
				return cached;
			}
			
			this.internalMetrics.countMiss("appinstancescanner.app");
			
			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "app");
			
			cached = Mono.zip(Mono.just(orgIdString), Mono.just(spaceIdString), Mono.just(applicationNameString))
				// start the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().start();
					return tuple.getT1();
				})
				.flatMap(triple -> {
					return this.cfAccessor.retrieveApplicationId(triple.getT1(), triple.getT2(), triple.getT3());
				}).flatMap(response -> {
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
				// stop the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().stop();
					return tuple.getT1();
				}).cache();
			
			this.applicationMap.put(key, cached);
			return cached;
		}
	}
	
	private Mono<String> getApplicationUrl(String applicationId, String protocol) {
		String key = applicationId;

		synchronized(key.intern()) {
			Mono<String> cached = this.hostnameMap.get(key);
			if (cached != null) {
				this.internalMetrics.countHit("appinstancescanner.route");
				return cached;
			}
			
			this.internalMetrics.countMiss("appinstancescanner.route");
			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "route");
			
			Mono<RouteEntity> routeMono = Mono.just(applicationId)
				// start the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().start();
					return tuple.getT1();
				})
				.flatMap(appId -> {
					return this.cfAccessor.retrieveRouteMapping(appId);
				})
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
				})
				// stop the timer
				.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
					tuple.getT2().stop();
					return tuple.getT1();
				}).cache();
			
			this.hostnameMap.put(key, applicationUrlMono);
			
			return applicationUrlMono;
		}
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
