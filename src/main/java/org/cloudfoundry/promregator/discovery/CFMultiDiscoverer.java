package org.cloudfoundry.promregator.discovery;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.validation.constraints.Null;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.servicebindings.ServiceBindingResource;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.ListUserProvidedServiceInstancesResponse;
import org.cloudfoundry.client.v2.userprovidedserviceinstances.UserProvidedServiceInstanceResource;
import org.cloudfoundry.promregator.auth.AuthenticationEnricher;
import org.cloudfoundry.promregator.auth.BasicAuthenticationEnricher;
import org.cloudfoundry.promregator.auth.NullEnricher;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.BasicAuthenticationConfiguration;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ResolvedTarget;
import org.cloudfoundry.promregator.scanner.ScannerUtils;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class CFMultiDiscoverer implements CFDiscoverer {
	private static final Logger log = Logger.getLogger(CFMultiDiscoverer.class);

	private static final AuthenticationEnricher NULL_ENRICHER = new NullEnricher();
	
	@Autowired
	private TargetResolver targetResolver;
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@Autowired
	private PromregatorConfiguration promregatorConfiguration;

	@Autowired
	private JmsTemplate jmsTemplate;
	
	@Autowired
	private CFAccessor cfAccessor;

	@Autowired
	private Clock clock;
	
	private Map<Instance, Instant> instanceExpiryMap = new ConcurrentHashMap<>();
	
	@Value("${promregator.discoverer.timeout:600}")
	private int expiryTimeout;
	
	@Null
	public List<Instance> discover(@Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) {
		
		Flux<Instance> configurationTargetDiscoveryFlux = Mono.fromCallable( () -> this.discoverConfigurationTargets(applicationIdFilter, instanceFilter))
		.subscribeOn(Schedulers.elastic())
		.elapsed()
		.map(tuple -> {
			Long time = tuple.getT1();
			log.debug(String.format("Configuration-Target-based discovery returned after %dms", time));
			return tuple.getT2();
		})
		.flatMapMany(list -> Flux.fromIterable(list))
		.log(CFDiscoverer.class.getCanonicalName()+".configurationTargetDiscovery")
		.doOnError(e -> {
			log.error("Error while performing configuration-target-based discovery", e);
		}).onErrorResume(__ -> Flux.empty());
		
		Flux<Instance> upsDiscoveryFlux = Mono.fromCallable( () -> this.discoverUserProvidedServices(applicationIdFilter, instanceFilter))
		.subscribeOn(Schedulers.elastic())
		.elapsed()
		.map(tuple -> {
			Long time = tuple.getT1();
			log.debug(String.format("UPS-based discovery returned after %dms", time));
			return tuple.getT2();
		})
		.flatMapMany(list -> Flux.fromIterable(list))
		.log(CFDiscoverer.class.getCanonicalName()+".upsBasedDiscovery")
		.doOnError(e -> {
			log.error("Error while performing UPS-based discovery", e);
		}).onErrorResume(__ -> Flux.empty());

		List<Instance> instanceList = Flux.merge(configurationTargetDiscoveryFlux, upsDiscoveryFlux)
		.collectList().block();
		
		if (instanceList != null) {
			// ensure that the instances are registered / touched properly
			for (Instance instance : instanceList) {
				this.registerInstance(instance);
			}
		}
		
		return instanceList;
	}

	private List<Instance> discoverConfigurationTargets(Predicate<? super String> applicationIdFilter,
			Predicate<? super Instance> instanceFilter) {
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

	private List<Instance> discoverUserProvidedServices(@Null Predicate<? super String> applicationIdFilter,
			@Null Predicate<? super Instance> instanceFilter) {
		
		// TODO: Handle errors here much more properly!
		
		Mono<ListUserProvidedServiceInstancesResponse> upsListMono = this.cfAccessor.retrieveAllUserProvidedServicesPromregatorRelevant();
		
		HashMap<String, Map<String, Object>> mapUPS2Credentials = new HashMap<>();
		HashMap<String, String> mapUPS2SpaceId = new HashMap<>();
		
		Flux<UserProvidedServiceInstanceResource> promregatorUPSFlux = upsListMono.map(resp -> resp.getResources())
		.flatMapMany(res -> {
			return Flux.fromIterable(res);
		}).doOnNext(item -> {
			String upsId = item.getMetadata().getId();

			Map<String, Object> creds = item.getEntity().getCredentials();
			mapUPS2Credentials.put(upsId, creds);
			
			String spaceId = item.getEntity().getSpaceId();
			mapUPS2SpaceId.put(upsId, spaceId);
		});
		
		/* Stream 2: Retrieve Application Ids via UPS bindings */
		HashMap<String, List<String>> mapUPS2ApplicationIds = new HashMap<>();
		
		Flux<ServiceBindingResource> stream2 = promregatorUPSFlux.map(item -> item.getMetadata().getId())
		.flatMap(upsId -> this.cfAccessor.retrieveUserProvidedServiceBindings(upsId))
		.map(resp -> resp.getResources())
		.flatMap(list -> Flux.fromIterable(list));
		
		if (applicationIdFilter != null) {
			stream2 = stream2.filter(binding -> applicationIdFilter.test(binding.getEntity().getApplicationId()));
		}
		
		stream2 = stream2.doOnNext(binding -> {
			String upsId = binding.getEntity().getServiceInstanceId();
			String applicationId = binding.getEntity().getApplicationId();
			
			// Warning! There may be multiple ApplicationIds per UPSId 
			// and there might also be multiple bindings between the application and the same UPS!
			if (!mapUPS2ApplicationIds.containsKey(upsId)) {
				mapUPS2ApplicationIds.put(upsId, new LinkedList<>());
			}
			
			List<String> applicationIds = mapUPS2ApplicationIds.get(upsId);
			applicationIds.add(applicationId);
		});

		/* Note, that due to the (optional) applicationIdFilter, we also only want to process those further, 
		 * which we require. Stream2 comes to rescue!
		 */
		
		Flux<String> appFileredSpaceIdFlux = stream2.map(binding -> {
			String upsId = binding.getEntity().getServiceInstanceId();
			/* Note that mapUPS2SpaceId was already filled with the doOnNext() above! */
			return mapUPS2SpaceId.get(upsId);
		}).distinct()
		.cache();

		/* Stream 1: Retrieve org/space information */
		HashMap<String, String> mapSpaceId2SpaceName = new HashMap<>();
		HashMap<String, String> mapSpaceId2OrgId = new HashMap<>();
		HashMap<String, String> mapOrgId2OrgName = new HashMap<>();
		
		Flux<GetOrganizationResponse> stream1 = appFileredSpaceIdFlux.flatMap(spaceId -> this.cfAccessor.retrieveSpace(spaceId))
		.doOnNext(space -> {
			String spaceId = space.getMetadata().getId();
			String spaceName = space.getEntity().getName();
			mapSpaceId2SpaceName.put(spaceId, spaceName);
			
			String orgId = space.getEntity().getOrganizationId();
			mapSpaceId2OrgId.put(spaceId, orgId);
		}).map(space -> space.getEntity().getOrganizationId())
		.distinct()
		.flatMap(orgId -> this.cfAccessor.retrieveOrg(orgId))
		.doOnNext(org -> {
			String orgId = org.getMetadata().getId();
			String orgName = org.getEntity().getName();
			mapOrgId2OrgName.put(orgId, orgName);
		});
		
		/* Stream 3: Retrieve SpaceApplicationSummaries based on applicationIds */
		HashMap<String, SpaceApplicationSummary> mapApplicationId2spaceSummaryApplication = new HashMap<>();
		
		Flux<SpaceApplicationSummary> stream3 = appFileredSpaceIdFlux.flatMap(spaceId -> this.cfAccessor.retrieveSpaceSummary(spaceId))
		.map(resp -> resp.getApplications())
		.flatMap(list -> Flux.fromIterable(list))
		.doOnNext(spaceSummaryApplication -> {
			String applicationId = spaceSummaryApplication.getId();
			mapApplicationId2spaceSummaryApplication.put(applicationId, spaceSummaryApplication);
		});
		
		/* wait for all streams to complete (which automatically fills all our HashMaps */
		Mono.when(stream1, stream3).block();
		
		/* fiddle everything together */
		List<Instance> instances = new LinkedList<>();
		for (Entry<String, List<String>> ups2AppIdsEntry : mapUPS2ApplicationIds.entrySet()) {
			String upsId = ups2AppIdsEntry.getKey();
			String spaceId = mapUPS2SpaceId.get(upsId);
			String spaceName = mapSpaceId2SpaceName.get(spaceId);
			String orgId = mapSpaceId2OrgId.get(spaceId);
			String orgName = mapOrgId2OrgName.get(orgId);
			
			Map<String, Object> creds = mapUPS2Credentials.get(upsId);
			String path = (String) creds.get("path");
			String protocol = (String) creds.get("protocol");
			
			String username = (String) creds.get("username");
			String password = (String) creds.get("password");
			
			for (String appId : ups2AppIdsEntry.getValue()) {
				SpaceApplicationSummary spaceApplicationSummary = mapApplicationId2spaceSummaryApplication.get(appId);

				String applicationUrl = String.format("%s://%s", protocol, spaceApplicationSummary.getUrls().get(0));
				String accessUrl = ScannerUtils.determineAccessURL(applicationUrl, path);
				
				int numberOfInstances = spaceApplicationSummary.getInstances();
				
				for (int i = 0; i<numberOfInstances; i++) {
					AuthenticationEnricher ae = NULL_ENRICHER;
					if (username != null && password != null) {
						BasicAuthenticationConfiguration authenticatorConfig = new BasicAuthenticationConfiguration();
						authenticatorConfig.setUsername(username);
						authenticatorConfig.setPassword(password);
						ae = new BasicAuthenticationEnricher(authenticatorConfig);
					}
					// TODO also handle OAuth2XSUAAEnricher
					
					Instance inst = new UPSBasedInstance(String.format("%s:%d", appId, i), accessUrl, orgName, spaceName, spaceApplicationSummary.getName(), ae);
					instances.add(inst);
				}
			}
		}
		
		List<Instance> filteredInstances = new LinkedList<>();
		if (instanceFilter != null) {
			for (Instance inst : instances) {
				if (instanceFilter.test(inst)) {
					filteredInstances.add(inst);
				}
			}
		} else {
			filteredInstances = instances;
		}
		
		return filteredInstances;
	}
	
	private void registerInstance(Instance instance) {
		Instant timeout = nextTimeout();
		this.instanceExpiryMap.put(instance, timeout);
		// NB: If already in the map, then the timeout is overwritten => refreshing/touching
	}
	
	/**
	 * checks whether a given instance is known at this Discoverer (or may already have expired)
	 * @param instance the instance which shall be checked
	 * @return <code>true</code> if the instance is registered; <code>false</code> otherwise. Note that the response is independent whether the validity of the instance has already expired or not!
	 */
	public boolean isInstanceRegistered(Instance instance) {
		return this.instanceExpiryMap.containsKey(instance);
	}

	private Instant nextTimeout() {
		Instant timeout = Instant.now(this.clock).plus(this.expiryTimeout, ChronoUnit.SECONDS);
		
		return timeout;
	}
	
	/**
	 * checks the list of registered Instances to be expired.
	 * This method is automatically called by the Spring framework in regular intervals asynchronously.
	 */
	@Scheduled(fixedDelay=60*1000)
	public void cleanup() {
		Instant now = Instant.now(this.clock);
		
		for (Iterator<Entry<Instance, Instant>> it = this.instanceExpiryMap.entrySet().iterator(); it.hasNext();) {
			Entry<Instance, Instant> entry = it.next();
			
			if (entry.getValue().isAfter(now)) {
				// not ripe yet; skip
				continue;
			}
			
			log.info(String.format("Instance %s has timed out; cleaning up", entry.getKey()));
			
			// broadcast event to JMS topic, that the instance is to be deleted
			this.jmsTemplate.convertAndSend(MessageBusDestination.DISCOVERER_INSTANCE_REMOVED, entry.getKey());
			
			it.remove();
		}
	}
	
	public void setClock(Clock newClock) {
		this.clock = newClock;
	}}
