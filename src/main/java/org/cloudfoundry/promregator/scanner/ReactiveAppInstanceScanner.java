package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ApplicationEntity;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.routemappings.RouteMappingResource;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.client.v2.shareddomains.SharedDomainEntity;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.prometheus.client.Histogram.Timer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ReactiveAppInstanceScanner implements AppInstanceScanner {
	private static final Logger log = Logger.getLogger(ReactiveAppInstanceScanner.class);

	private PassiveExpiringMap<String, Mono<String>> orgMap;
	private PassiveExpiringMap<String, Mono<String>> spaceMap;
	private PassiveExpiringMap<String, Mono<String>> applicationMap;
	private PassiveExpiringMap<String, Flux<String>> applicationsInSpaceMap;
	private PassiveExpiringMap<String, Mono<String>> hostnameMap;
	private PassiveExpiringMap<String, Mono<String>> domainMap;

	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;

	@Value("${cf.cache.timeout.space:3600}")
	private int timeoutCacheSpaceLevel;

	@Value("${cf.cache.timeout.org:3600}")
	private int timeoutCacheOrgLevel;

	@Autowired
	private CFAccessor cfAccessor;

	@Autowired
	private InternalMetrics internalMetrics;

	private static class InternalInstance implements Cloneable {

		public Target target;

		public Mono<String> orgId;
		public Mono<String> spaceId;
		public Mono<String> applicationId;

		public String instanceId;

		public String accessUrl;

		@Override
		protected Object clone() throws CloneNotSupportedException {
			InternalInstance other = (InternalInstance) super.clone();
			other.target = this.target;
			other.orgId = this.orgId;
			other.spaceId = this.spaceId;
			other.applicationId = this.applicationId;

			other.instanceId = this.instanceId;

			other.accessUrl = accessUrl;
			return other;
		}

		public Instance toInstance() {
			return new Instance(this.target, this.instanceId, this.accessUrl);
		}

	}

	@PostConstruct
	public void setupMaps() {
		this.orgMap = new PassiveExpiringMap<>(this.timeoutCacheOrgLevel, TimeUnit.SECONDS);
		this.spaceMap = new PassiveExpiringMap<>(this.timeoutCacheSpaceLevel, TimeUnit.SECONDS);
		/*
		 * NB: There is little point in separating the timeouts between
		 * applicationMap and hostnameMap: - changes to routes may come easily
		 * and thus need to be detected fast - apps can start and stop, we need
		 * to see this, too - instances can be added to apps - Blue/green
		 * deployment may alter both of them
		 * 
		 * In short: both are very volatile and we need to query them often
		 */
		this.applicationMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.hostnameMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
		this.domainMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);

		/*
		 * NB: cache timeout of the mapping from a space to the list of apps
		 * should be in the same order as the cache timeout of the
		 * applicationMap; otherwise, there could be quite some strange race
		 * conditions between those two caches.
		 */
		this.applicationsInSpaceMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
	}

	public List<Instance> determineInstancesFromTargets(List<Target> targets) {
		LinkedList<Instance> result = new LinkedList<>();

		List<Target> resolvedTargets = this.resolveTargets(targets);

		Flux<Target> initialFlux = Flux.fromIterable(resolvedTargets);

		Flux<InternalInstance> initialInstancesOnlyApplication = initialFlux.map(target -> {
			InternalInstance i = new InternalInstance();

			i.target = target;

			i.orgId = getOrgId(i.target.getOrgName());
			i.spaceId = getSpaceId(i.orgId, i.target.getSpaceName());
			i.applicationId = getApplicationId(i.orgId, i.spaceId, i.target.getApplicationName());

			return i;
		});

		Flux<InternalInstance> instancesOfApplications = this.getInstances(initialInstancesOnlyApplication);

		instancesOfApplications.flatMap(instance -> {
			Mono<String> applUrlMono = this.getApplicationUrl(instance.applicationId, instance.target.getProtocol());

			Mono<String> accessUrlMono = Mono.zip(applUrlMono, Mono.just(instance.target.getPath())).map(tuple -> {
				String applUrl = tuple.getT1();
				if (!applUrl.endsWith("/")) {
					applUrl += '/';
				}

				String path = tuple.getT2();
				while (path.startsWith("/"))
					path = path.substring(1);

				return applUrl + path;
			});

			Mono<InternalInstance> newInstance = Mono.zip(Mono.just(instance), accessUrlMono).map(tuple -> {
				InternalInstance i = tuple.getT1();
				i.accessUrl = tuple.getT2();

				return i;
			});

			return newInstance;
		}).map(internalInstance -> internalInstance.toInstance()).toIterable().forEach(result::add);

		return result;
	}

	private List<Target> resolveTargets(List<Target> targets) {
		List<Target> resolvedTargets = new LinkedList<>();

		for (Target target : targets) {
			if (target.getApplicationName() != null) {
				// trivial case; does not require to be resolved
				resolvedTargets.add(target);
				continue;
			}

			/* NB: Now we have to consider two cases:
			 * Case 1: both applicationName and applicationRegex is empty => select all apps
			 * Case 2: applicationName is null, but applicationRegex is filled => filter all apps with the regex
			 * In both cases we need the list of all apps in the space.
			 */

			String orgName = target.getOrgName();
			String spaceName = target.getSpaceName();
			String key = String.format("%s|%s", orgName, spaceName);
			Flux<String> applicationsInSpace = this.applicationsInSpaceMap.get(key);
			if (applicationsInSpace == null) {
				// cache miss
				this.internalMetrics.countMiss("appinstancescanner.applicationsInSpace");

				// for retrieving all applications, we need the orgId and
				// spaceId the names are not sufficient
				Mono<String> orgIdMono = this.getOrgId(orgName);
				Mono<String> spaceIdMono = this.getSpaceId(orgIdMono, spaceName);

				ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "applicationsInSpace");

				Mono<ListApplicationsResponse> responseMono = Mono.zip(orgIdMono, spaceIdMono)
						.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
							tuple.getT2().start();
							return tuple.getT1();
						}).flatMap(tuple -> {
							return this.cfAccessor.retrieveAllApplicationIdsInSpace(tuple.getT1(), tuple.getT2());
						})// stop the timer
						.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
							tuple.getT2().stop();
							return tuple.getT1();
						}).cache();

				Flux<ApplicationResource> applicationResourceInSpace = responseMono
						.map(ListApplicationsResponse::getResources).flatMapMany(Flux::fromIterable)
						.filter(ar -> isApplicationInScrapableState(ar.getEntity().getState()));

				Flux<ApplicationResource> filteredApplicationResource = applicationResourceInSpace;
				if (target.getApplicationRegex() != null) {
					final Pattern filterPattern = Pattern.compile(target.getApplicationRegex());
					
					filteredApplicationResource = applicationResourceInSpace.filter(ar -> {
						Matcher m = filterPattern.matcher(ar.getEntity().getName());
						return m.matches();
					});
				}
				
				applicationsInSpace = filteredApplicationResource.map(ApplicationResource::getEntity)
						.map(ApplicationEntity::getName);
				this.applicationsInSpaceMap.put(key, applicationsInSpace);

				/*
				 * Note that we can perform a great performance optimization
				 * here: applicationResourceInSpace contains all information
				 * required to fill also applicationMap. If this is filled,
				 * further single roundtrips are no longer necessary for
				 * fetching the id of the application.
				 */
				applicationResourceInSpace.subscribe(ar -> {
					String applicationId = ar.getMetadata().getId();

					String applicationMapKey = this.determineApplicationMapKey(orgIdMono, spaceIdMono,
							ar.getEntity().getName());

					this.applicationMap.putIfAbsent(applicationMapKey, Mono.just(applicationId));
				});

			} else {
				this.internalMetrics.countHit("appinstancescanner.applicationsInSpace");
			}

			Iterable<String> applicationNames = applicationsInSpace.toIterable();

			for (String appName : applicationNames) {
				Target newTarget = null;
				try {
					newTarget = (Target) target.clone();
				} catch (CloneNotSupportedException e) {
					log.error("Cloning exception raised, even though not expected", e);
					continue;
				}
				newTarget.setApplicationName(appName);

				resolvedTargets.add(newTarget);
			}
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
				}).flatMap(orgName -> {
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
				}).cache();

		this.orgMap.put(orgNameString, cached);
		return cached;
	}

	private Mono<String> getSpaceId(Mono<String> orgIdMono, String spaceNameString) {
		String key = String.format("%d|%s", orgIdMono.hashCode(), spaceNameString);

		synchronized (key.intern()) {
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
					}).flatMap(tuple -> {
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

	private Mono<String> getApplicationId(Mono<String> orgIdMono, Mono<String> spaceIdMono,
			String applicationNameString) {
		String key = determineApplicationMapKey(orgIdMono, spaceIdMono, applicationNameString);
		synchronized (key.intern()) {
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
					}).flatMap(triple -> {
						return this.cfAccessor.retrieveApplicationId(triple.getT1(), triple.getT2(), triple.getT3());
					}).flatMap(response -> {
						List<ApplicationResource> resources = response.getResources();
						if (resources == null) {
							return Mono.empty();
						}

						if (resources.isEmpty()) {
							log.warn(String.format("Received empty result on requesting application %s",
									applicationNameString));
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

	private String determineApplicationMapKey(Mono<String> orgIdMono, Mono<String> spaceIdMono,
			String applicationNameString) {
		String key = String.format("%d|%d|%s", orgIdMono.hashCode(), spaceIdMono.hashCode(), applicationNameString);
		return key;
	}

	private Mono<String> getApplicationUrl(Mono<String> applicationIdMono, String protocol) {
		String key = String.format("%d", applicationIdMono.hashCode());

		synchronized (key.intern()) {
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
					}).flatMap(appId -> {
						return this.cfAccessor.retrieveRouteMapping(appId);
					}).flatMap(mappingResponse -> {
						List<RouteMappingResource> resourceList = mappingResponse.getResources();
						if (resourceList.isEmpty())
							return Mono.empty();

						String routeId = resourceList.get(0).getEntity().getRouteId();
						if (routeId == null)
							return Mono.empty();

						return this.cfAccessor.retrieveRoute(routeId);
					}).flatMap(GetRouteResponse -> {
						RouteEntity route = GetRouteResponse.getEntity();
						if (route == null)
							return Mono.empty();

						// WARNING! route.getApplicationsUrl() is the URL back
						// to the application
						// and not the URL which points to the endpoint of the
						// cell!
						return Mono.just(route);
					});

			Mono<String> domainMono = routeMono.map(route -> route.getDomainId()).flatMap(domainId -> {
				return this.getDomain(domainId);
			});

			Mono<String> applicationUrlMono = Mono.zip(domainMono, routeMono).map(tuple -> {
				String domain = tuple.getT1();
				RouteEntity route = tuple.getT2();

				String url = String.format("%s://%s.%s", protocol, route.getHost(), domain);
				if (route.getPath() != null) {
					url += "/" + route.getPath();
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
					}).flatMap(domainId -> {
						return this.cfAccessor.retrieveSharedDomain(domainId);
					}).map(response -> {
						SharedDomainEntity sharedDomain = response.getEntity();

						return sharedDomain.getName();
					})
					// stop the timer
					.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
						tuple.getT2().stop();
						return tuple.getT1();
					}).cache();

			this.domainMap.put(key, cached);
			return cached;
		}
	}

	private Flux<InternalInstance> getInstances(Flux<InternalInstance> instancesFlux) {
		Flux<InternalInstance> allInstances = instancesFlux.flatMap(instance -> {
			ReactiveTimer reactiveTimer = new ReactiveTimer(this.internalMetrics, "instances");

			Mono<ListProcessesResponse> processesResponse = Mono
					.zip(instance.orgId, instance.spaceId, instance.applicationId)
					// start the timer
					.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
						tuple.getT2().start();
						return tuple.getT1();
					}).flatMap(tuple -> {
						String orgId = tuple.getT1();
						String spaceId = tuple.getT2();
						String appId = tuple.getT3();

						return this.cfAccessor.retrieveProcesses(orgId, spaceId, appId);
					})
					// stop the timer
					.zipWith(Mono.just(reactiveTimer)).map(tuple -> {
						tuple.getT2().stop();
						return tuple.getT1();
					});

			Flux<InternalInstance> fluxInstances = Mono
					.zip(Mono.just(instance), processesResponse, instance.applicationId).flatMapMany(tuple -> {
						InternalInstance inst = tuple.getT1();
						ListProcessesResponse response = tuple.getT2();
						String appId = tuple.getT3();

						List<ProcessResource> resourcesList = response.getResources();
						if (resourcesList.isEmpty())
							return Mono.empty();

						int instances = resourcesList.get(0).getInstances();

						List<InternalInstance> resultInstances = new LinkedList<>();
						for (int i = 0; i < instances; i++) {
							InternalInstance clone = null;
							try {
								clone = (InternalInstance) inst.clone();
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

	public void invalidateCacheApplications() {
		log.info("Invalidating application cache");
		this.applicationMap.clear();
		this.applicationsInSpaceMap.clear();
		this.hostnameMap.clear();
		this.domainMap.clear();
	}

	public void invalidateCacheSpace() {
		log.info("Invalidating space cache");
		this.spaceMap.clear();
	}

	public void invalidateCacheOrg() {
		log.info("Invalidating org cache");
		this.orgMap.clear();
	}

}
