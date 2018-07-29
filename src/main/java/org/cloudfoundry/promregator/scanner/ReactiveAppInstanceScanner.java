package org.cloudfoundry.promregator.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Null;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveAppInstanceScanner implements AppInstanceScanner {
	
	private static final Logger log = Logger.getLogger(ReactiveAppInstanceScanner.class);
	private static final String INVALID_ORG_ID = "***invalid***";

	private PassiveExpiringMap<String, Mono<String>> applicationUrlMap;
	
	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;


	@PostConstruct
	public void setupMaps() {
		this.applicationUrlMap = new PassiveExpiringMap<>(this.timeoutCacheApplicationLevel, TimeUnit.SECONDS);
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
	
	@Override
	public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets, @Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) {
		Flux<ResolvedTarget> targetsFlux = Flux.fromIterable(targets);
		
		Flux<OSAVector> initialOSAVectorFlux = targetsFlux.map(target -> {
			OSAVector v = new OSAVector();
			v.target = target;
			
			return v;
		});
		
		Flux<String> orgIdFlux = initialOSAVectorFlux.flatMapSequential(v -> this.getOrgId(v.target.getOrgName()));
		Flux<OSAVector> OSAVectorOrgFlux = Flux.zip(initialOSAVectorFlux, orgIdFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			if (INVALID_ORG_ID.equals(tuple.getT2())) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			v.orgId = tuple.getT2();
			return Mono.just(v);
		});
		
		Flux<String> spaceIdFlux = OSAVectorOrgFlux.flatMapSequential(v -> this.getSpaceId(v.orgId, v.target.getSpaceName()));
		Flux<OSAVector> OSAVectorSpaceFlux = Flux.zip(OSAVectorOrgFlux, spaceIdFlux).map(tuple -> {
			OSAVector v = tuple.getT1();
			v.spaceId = tuple.getT2();
			return v;
		});
		
		Flux<String> applicationIdFlux = OSAVectorSpaceFlux.flatMapSequential(v -> this.getApplicationId(v.orgId, v.spaceId, v.target.getApplicationName()));
		Flux<OSAVector> OSAVectorApplicationFlux = Flux.zip(OSAVectorSpaceFlux, applicationIdFlux).map(tuple -> {
			OSAVector v = tuple.getT1();
			v.applicationId = tuple.getT2();
			return v;
		});
		
		// perform pre-filtering, if available
		if (applicationIdFilter != null) {
			OSAVectorApplicationFlux = OSAVectorApplicationFlux.filter(v -> applicationIdFilter.test(v.applicationId));
		}
		
		Flux<SpaceApplicationSummary> applicationSummaryFlux = OSAVectorApplicationFlux.flatMapSequential( v -> this.getApplicationSummary(v.spaceId, v.applicationId));
		Flux<OSAVector> OSAVectorCompleteFlux = Flux.zip(OSAVectorApplicationFlux, applicationSummaryFlux).map(tuple-> {
			OSAVector v = tuple.getT1();
			SpaceApplicationSummary summary = tuple.getT2();
			
			List<String> urls = summary.getUrls();
			if (urls != null && !urls.isEmpty()) {
				String url = String.format("%s://%s", v.target.getProtocol(), urls.get(0));
				v.accessURL = this.determineAccessURL(url, v.target.getPath());
			}
			
			v.numberOfInstances = summary.getInstances();
			
			return v;
		});
		
		Flux<Instance> instancesFlux = OSAVectorCompleteFlux.flatMapSequential(v -> {
			List<Instance> instances = new ArrayList<>(v.numberOfInstances);
			for (int i = 0; i<v.numberOfInstances; i++) {
				Instance inst = new Instance(v.target, String.format("%s:%d", v.applicationId, i), v.accessURL);
				instances.add(inst);
			}
			
			return Flux.fromIterable(instances);
		});
		
		// perform pre-filtering, if available
		if (instanceFilter != null) {
			instancesFlux = instancesFlux.filter(instanceFilter);
		}
		
		
		Mono<List<Instance>> listInstancesMono = instancesFlux.collectList();
		
		List<Instance> result = null;
		try {
			result = listInstancesMono.block();
		} catch (RuntimeException e) {
			log.error("Error during retrieving the instances of a list of targets", e);
			result = null;
		}
		
		return result;
	}
	
	private Mono<String> getOrgId(String orgNameString) {
		Mono<String> orgId = this.cfAccessor.retrieveOrgId(orgNameString).flatMap(response -> {
			List<OrganizationResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_ORG_ID);
			}
			
			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting org %s", orgNameString));
				return Mono.just(INVALID_ORG_ID);
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
	
	private Mono<SpaceApplicationSummary> getApplicationSummary(String spaceIdString, String applicationIdString) {
		return this.cfAccessor.retrieveSpaceSummary(spaceIdString)
			.flatMapMany( spaceSummary -> {
				return Flux.fromIterable(spaceSummary.getApplications());
			})
			.filter(summary -> applicationIdString.equals(summary.getId()))
			.single();
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
	
	public void invalidateApplicationUrlCache() {
		this.applicationUrlMap.clear();
	}
}
