package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsRequest;
import org.cloudfoundry.client.v2.routemappings.RouteMappingResource;
import org.cloudfoundry.client.v2.routes.GetRouteRequest;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainRequest;
import org.cloudfoundry.client.v2.shareddomains.SharedDomainEntity;
import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v3.organizations.OrganizationResource;
import org.cloudfoundry.client.v3.processes.ListProcessesRequest;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v3.spaces.SpaceResource;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.prometheus.client.Histogram.Timer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ReactiveAppInstanceScanner {
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

	@Autowired
	private ReactorCloudFoundryClient cloudFoundryClient;
	
	@Autowired
	private InternalMetrics internalMetrics;

	public static class Instance implements Cloneable {
		
		public Target target;
		
		public Mono<String> orgId;
		public Mono<String> spaceId;
		public Mono<String> applicationId;
		
		public String instanceId;
		
		public String accessUrl;

		@Override
		protected Object clone() throws CloneNotSupportedException {
			Instance other = new Instance();
			other.target = this.target;
			other.orgId = this.orgId;
			other.spaceId = this.spaceId;
			other.applicationId = this.applicationId;
			
			other.instanceId = this.instanceId;
			
			other.accessUrl = accessUrl;
			return other;
		}
		
	}
	
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
	
	
	public List<Instance> determineInstancesFromTargets(List<Target> targets) {
		LinkedList<Instance> result = new LinkedList<>();
		
		Flux<Target> initialFlux = Flux.fromIterable(targets);
		
		Flux<Instance> initialInstancesOnlyApplication = initialFlux.map( target -> {
			Instance i = new Instance();
			
			i.target = target;
			
			i.orgId = getOrgId(i.target.getOrgName());
			i.spaceId = getSpaceId(i.orgId, i.target.getSpaceName());
			i.applicationId = getApplicationId(i.orgId, i.spaceId, i.target.getApplicationName());
			
			return i;
		});
		
		Flux<Instance> instancesOfApplications = this.getInstances(initialInstancesOnlyApplication);
		
		instancesOfApplications.flatMap(instance -> {
			Mono<String> applUrlMono = this.getApplicationUrl(instance.applicationId);
			
			Mono<String> accessUrlMono = Mono.zip(applUrlMono, Mono.just(instance.target.getPath()))
			.map(tuple -> {
				String applUrl = tuple.getT1();
				if (!applUrl.endsWith("/")) {
					applUrl += '/';
				}
				
				String path = tuple.getT2();
				while (path.startsWith("/"))
					path = path.substring(1);
				
				return applUrl + path;
			});
			
			Mono<Instance> newInstance = Mono.zip(Mono.just(instance), accessUrlMono)
			.map(tuple -> {
				Instance i = tuple.getT1();
				i.accessUrl = tuple.getT2();
				
				return i;
			});
			
			return newInstance;
		}).toIterable().forEach(result::add);
		
		return result;
	}

	private static class ReactiveTimer {
		private Timer t;
		private final InternalMetrics im;
		private final String requestType;
		
		public ReactiveTimer(final InternalMetrics im, final String requestType) {
			this.im = im;
			this.requestType = requestType;
		}
		
		public void start() {
			this.t = this.im.startTimerCFFetch(this.requestType);
		}

		public void stop() {
			if (this.t != null) {
				this.t.observeDuration();
			}
		}
		
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
			ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
			return this.cloudFoundryClient.organizationsV3().list(orgsRequest).log("Query Org");
		}).flatMap(response -> {
			List<OrganizationResource> resources = response.getResources();
			if (resources == null) {
				return Mono.empty();
			}
			
			OrganizationResource organizationResource = resources.get(0);
			return Mono.just(organizationResource.getId());
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

	private Mono<String> getSpaceId(Mono<String> orgIdMono, String spaceNameString) {
		String key = String.format("%d|%s", orgIdMono.hashCode(), spaceNameString);
		Mono<String> cached = this.spaceMap.get(key);
		if (cached != null) {
			this.internalMetrics.countHit("appinstancescanner.space");
			return cached;
		}
		
		this.internalMetrics.countMiss("appinstancescanner.space");
		
		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "space");
		
		cached = Mono.zip(orgIdMono, Mono.just(spaceNameString))
			// start the timer
			.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
				tuple.getT2().start();
				return tuple.getT1();
			})
			.flatMap(tuple -> {
			ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(tuple.getT1()).name(tuple.getT2()).build();
			return this.cloudFoundryClient.spacesV3().list(spacesRequest).log("Query Space");
		}).flatMap(response -> {
			List<SpaceResource> resources = response.getResources();
			if (resources == null) {
				return Mono.empty();
			}
			
			SpaceResource spaceResource = resources.get(0);
			return Mono.just(spaceResource.getId());
		})
		// stop the timer
		.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
			tuple.getT2().stop();
			return tuple.getT1();
		}).cache();
		
		this.spaceMap.put(key, cached);
		return cached;
	}


	private Mono<String> getApplicationId(Mono<String> orgIdMono, Mono<String> spaceIdMono, String applicationNameString) {
		String key = String.format("%d|%d|%s", orgIdMono.hashCode(), spaceIdMono.hashCode(), applicationNameString);
		Mono<String> cached = this.applicationMap.get(key);
		if (cached != null) {
			this.internalMetrics.countHit("appinstancescanner.app");
			return cached;
		}
		
		this.internalMetrics.countMiss("appinstancescanner.app");
		
		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "app");
		
		cached = Mono.zip(orgIdMono, spaceIdMono, Mono.just(applicationNameString))
			// start the timer
			.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
				tuple.getT2().start();
				return tuple.getT1();
			})
			.flatMap(triple -> {
			ListApplicationsRequest request = ListApplicationsRequest.builder()
					.organizationId(triple.getT1())
					.spaceId(triple.getT2())
					.name(triple.getT3())
					.build();
			return this.cloudFoundryClient.applicationsV3().list(request).log("Query App");
		}).flatMap(response -> {
			List<ApplicationResource> resources = response.getResources();
			if (resources == null) {
				return Mono.empty();
			}
			
			ApplicationResource applicationResource = resources.get(0);
			return Mono.just(applicationResource.getId());
		})
		// stop the timer
		.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
			tuple.getT2().stop();
			return tuple.getT1();
		}).cache();
		
		this.applicationMap.put(key, cached);
		return cached;
	}
	
	private Mono<String> getApplicationUrl(Mono<String> applicationIdMono) {
		String key = String.format("%d", applicationIdMono.hashCode());

		Mono<String> cached = this.hostnameMap.get(key);
		if (cached != null) {
			this.internalMetrics.countHit("appinstancescanner.route");
			return cached;
		}
		
		this.internalMetrics.countMiss("appinstancescanner.route");

		ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "route");
		
		Mono<RouteEntity> routeMono = applicationIdMono
		// start the timer
		.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
			tuple.getT2().start();
			return tuple.getT1();
		})
		.flatMap(appId -> {
			ListRouteMappingsRequest mappingRequest = ListRouteMappingsRequest.builder().applicationId(appId).build();
			return this.cloudFoundryClient.routeMappings().list(mappingRequest).log("Query Route Mapping");
		}).flatMap(mappingResponse -> {
			List<RouteMappingResource> resourceList = mappingResponse.getResources();
			if (resourceList.isEmpty())
				return Mono.empty();
			
			String routeId = resourceList.get(0).getEntity().getRouteId();
			if (routeId == null)
				return Mono.empty();
			
			GetRouteRequest getRequest = GetRouteRequest.builder().routeId(routeId).build();
			return this.cloudFoundryClient.routes().get(getRequest).log("Get Route");
		}).flatMap(GetRouteResponse -> {
			RouteEntity route = GetRouteResponse.getEntity();
			if (route == null)
				return Mono.empty();
			
			// WARNING! route.getApplicationsUrl() is the URL back to the application
			// and not the URL which points to the endpoint of the cell!
			return Mono.just(route);
		});
		
		Mono<String> domainMono = routeMono.map(route -> route.getDomainId())
		.flatMap(domainId -> {
			return this.getDomain(domainId);
		});
		
		Mono<String> applicationUrlMono = Mono.zip(domainMono, routeMono)
		.map(tuple -> {
			String domain = tuple.getT1();
			RouteEntity route = tuple.getT2();
			
			String url = String.format("http://%s.%s", route.getHost(), domain);
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

	private Mono<String> getDomain(String domainIdString) {
		String key = domainIdString;
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
			GetSharedDomainRequest domainRequest = GetSharedDomainRequest.builder().sharedDomainId(domainId).build();
			return this.cloudFoundryClient.sharedDomains().get(domainRequest).log("Get Domain");
		}).map(response -> {
			SharedDomainEntity sharedDomain = response.getEntity();
			
			return sharedDomain.getName();
		})
		// stop the timer
		.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
			tuple.getT2().stop();
			return tuple.getT1();
		})
		.cache();
		
		this.domainMap.put(key, cached);
		return cached;
	}
	
	private Flux<Instance> getInstances(Flux<Instance> instancesFlux) {
		Flux<Instance> allInstances = instancesFlux.flatMap(instance -> {
			Mono<ListProcessesResponse> processesResponse = Mono.zip(instance.orgId, instance.spaceId, instance.applicationId)
			.flatMap(tuple -> {
				String orgId = tuple.getT1();
				String spaceId = tuple.getT2();
				String appId = tuple.getT3();
				
				ListProcessesRequest request = ListProcessesRequest.builder().organizationId(orgId).spaceId(spaceId).applicationId(appId).build();
				return this.cloudFoundryClient.processes().list(request).log("List Processes");
			});
			
			Flux<Instance> fluxInstances = Mono.zip(Mono.just(instance), processesResponse, instance.applicationId)
			.flatMapMany(tuple -> {
				Instance inst = tuple.getT1();
				ListProcessesResponse response = tuple.getT2();
				String appId = tuple.getT3();
				
				List<ProcessResource> resourcesList = response.getResources();
				if (resourcesList.isEmpty())
					return Mono.empty();
				
				int instances = resourcesList.get(0).getInstances();
				
				List<Instance> resultInstances = new LinkedList<>();
				for (int i = 0; i<instances; i++) {
					Instance clone = null;
					try {
						clone = (Instance) inst.clone();
					} catch (Exception e) {
						// may not happen
						return Mono.empty();
					}
					clone.instanceId = String.format("%s:%d", appId, i);
					resultInstances.add(clone);
				}
				
				return Flux.fromIterable(resultInstances);
			});
			
			return fluxInstances;
		});
		
		return allInstances;
	}
	
}
