package org.cloudfoundry.promregator.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.logging.log4j.util.Strings;
import org.cloudfoundry.client.v3.ToOneRelationship;
import org.cloudfoundry.client.v3.applications.ListApplicationProcessesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.domains.GetDomainResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveAppInstanceScannerV3 implements AppInstanceScanner {

	private static final Logger log = LoggerFactory.getLogger(ReactiveAppInstanceScannerV3.class);

	/**
	 * OSA stands for Org-Space-Application
	 */
	private static class OSAVector {
		private ResolvedTarget target;

		private String domainId;
		private String accessURL;
		private int numberOfInstances;
		private boolean internal;
		private int internalRoutePort;

		/**
		 * @return the target
		 */
		public ResolvedTarget getTarget() {
			return target;
		}

		public String getDomainId() {
			return domainId;
		}

		public void setDomainId(String domainId) {
			this.domainId = domainId;
		}

		public int getInternalRoutePort() {
			return internalRoutePort;
		}

		public void setInternalRoutePort(int internalRoutePort) {
			this.internalRoutePort = internalRoutePort;
		}

		/**
		 * @param target the target to set
		 */
		public void setTarget(ResolvedTarget target) {
			this.target = target;
		}

		/**
		 * @return the accessURL
		 */
		public String getAccessURL() {
			return accessURL;
		}

		/**
		 * @param accessURL the accessURL to set
		 */
		public void setAccessURL(String accessURL) {
			this.accessURL = accessURL;
		}

		/**
		 * @return the isInternal
		 */
		public boolean isInternal() {
			return internal;
		}

		/**
		 * @param isInternal the isInternal to set
		 */
		public void setInternal(boolean isInternal) {
			this.internal = isInternal;
		}

		/**
		 * @return the numberOfInstances
		 */
		public int getNumberOfInstances() {
			return numberOfInstances;
		}

		/**
		 * @param numberOfInstances the numberOfInstances to set
		 */
		public void setNumberOfInstances(int numberOfInstances) {
			this.numberOfInstances = numberOfInstances;
		}

	}
	
	@Autowired
	private CFAccessor cfAccessor;
	
	@Value("${promregator.defaultInternalRoutePort:8080}")
	private int defaultInternalRoutePort;
	
	@Override
	public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets,
			Predicate<? super String> applicationIdFilter, 
			Predicate<? super Instance> instanceFilter) {
		
		Stream<ResolvedTarget> targetsStream = targets.stream();
		
		if (applicationIdFilter != null) {
			targetsStream = targetsStream.filter(t -> applicationIdFilter.test(t.getApplicationId()));
		}
		
		Flux<OSAVector> initialOSAVectorFlux = Flux.fromStream(targetsStream)
				.map(rt -> {
					OSAVector osaVector = new OSAVector();
					osaVector.setTarget(rt);
					return osaVector;
				})
				.cache();
		
		/*
		 * For V3 it is no longer possible to get the SpaceSummary.
		 * This implies that we need to retrieve data on application level :-(
		 * Instead, the instance count can be found at the Processes endpoint.
		 * The ApplicationURL is buried in the Routes.
		 * Fortunately, we can retrieve this information in parallel.
		 */
		
		Flux<ListApplicationProcessesResponse> webProcessForAppFlux = initialOSAVectorFlux.flatMap(rt -> this.cfAccessor.retrieveWebProcessesForApp(rt.getTarget().getApplicationId()));
		
		Flux<OSAVector> numberInstancesOSAVectorFlux = Flux.zip(initialOSAVectorFlux, webProcessForAppFlux).flatMap(tuple -> {
			final OSAVector osaVector = tuple.getT1();
			final ResolvedTarget rt = osaVector.getTarget();
			final ListApplicationProcessesResponse lapr = tuple.getT2();
			
			List<ProcessResource> list = lapr.getResources();
			if (list.size() > 1) {
				log.error(String.format("Application Id %s with application name %s in org %s and space %s returned multiple web processes via CF API V3 Processes; Promregator does not know how to handle this. Provide your use case to the developers to understand how this shall be handled properly.", rt.getApplicationId(), rt.getApplicationName(), rt.getOrgName(), rt.getSpaceName()));
				return Mono.empty();
			}
			
			if (list.size() == 0) {
				log.error(String.format("Application Id %s with application name %s in org %s and space %s returned no web processes via CF API V3 Processes; Promregator does not know how to handle this. Provide your use case to the developers to understand how this shall be handled properly.", rt.getApplicationId(), rt.getApplicationName(), rt.getOrgName(), rt.getSpaceName()));
				return Mono.empty();
			}
			
			ProcessResource pr = list.get(0);
			final int numberInstances = pr.getInstances();
			osaVector.setNumberOfInstances(numberInstances);
			return Mono.just(osaVector);
		});
		
		/*
		 * We need to retrieve the app's routes.
		 * We already know the applicationId; we don't need to retrieve the
		 * base information of the app. Yet, there is only a single-retrieve
		 * endpoint available in the CFCC for getting routes for an app.
		 */
		Flux<ListApplicationRoutesResponse> applicationRoutesFlux = initialOSAVectorFlux.flatMap(v -> this.cfAccessor.retrieveRoutesForAppIdV3(v.getTarget().getApplicationId()));
		Flux<OSAVector> domainIdOSAVectorFlux = Flux.zip(initialOSAVectorFlux, applicationRoutesFlux).flatMap(tuple -> {
			final OSAVector osaVector = tuple.getT1();
			final ResolvedTarget rt = osaVector.getTarget();
			ListApplicationRoutesResponse larr = tuple.getT2();
			
			List<String> urls = new ArrayList<>(larr.getResources().size());
			for (RouteResource rr : larr.getResources()) {
				urls.add(rr.getUrl());
			}
			
			if (urls.isEmpty()) {
				/*
				 * There is no URL provided for this app
				 * We are skipping this app.
				 */
				log.info(String.format("There is no URL provided for application id %s, application %s in space %s of organization %s; this application is being skipped", rt.getApplicationId(), rt.getApplicationName(), rt.getSpaceName(), rt.getOrgName()));
				return Mono.empty();
			}
			
			String accessUrl = RouteUtils.determineApplicationRoute(urls, rt.getOriginalTarget().getPreferredRouteRegexPatterns());
			osaVector.setAccessURL(accessUrl);
			
			/*
			 * We need to figure out the domain which we have selected above
			 */
			Optional<RouteResource> selectedRouteResourceOptional = larr.getResources().stream().filter(rr -> rr.getUrl().equals(accessUrl)).findFirst();
			if (selectedRouteResourceOptional.isEmpty()) {
				log.error(String.format("Internal consistency error while trying to determine selected Route for application %s", rt.getApplicationId()));
				return Mono.empty();
			}
			RouteResource selectedRouteResource = selectedRouteResourceOptional.get();
			ToOneRelationship domain = selectedRouteResource.getRelationships().getDomain();
			String domainId = domain.getData().getId();
			
			osaVector.setDomainId(domainId);
			return Mono.just(osaVector);
		})
		.cache();
		
		Flux<GetDomainResponse> domainFlux = domainIdOSAVectorFlux.flatMap(osaVector -> this.cfAccessor.retrieveDomainV3(osaVector.getDomainId()));
		Flux<OSAVector> internalFlagOSAVectorFlux = Flux.zip(domainIdOSAVectorFlux, domainFlux).flatMap(tuple -> {
			OSAVector osaVector = tuple.getT1();
			GetDomainResponse dr = tuple.getT2();
			
			if (Strings.isNotEmpty(osaVector.getTarget().getOriginalTarget().getOverrideRouteAndPath())) {
				osaVector.setInternal(true);
			} else {
				osaVector.setInternal(dr.isInternal());
			}
			return Mono.just(osaVector);
		});

		Flux<Instance> instancesFlux = initialOSAVectorFlux.flatMapSequential(v -> {
			if (v.getNumberOfInstances() == 0 || v.getAccessURL() == null) {
				// this target was skipped before due to one or the other reason
				return Flux.empty();
			}
			
			List<Instance> instances = new ArrayList<>(v.getNumberOfInstances());
			for (int i = 0; i < v.numberOfInstances; i++) {
				Instance inst = new Instance(v.getTarget(), String.format("%s:%d", v.getTarget().getApplicationId(), i),
						v.getAccessURL(), v.isInternal());

				if(Strings.isNotEmpty(v.getTarget().getOriginalTarget().getOverrideRouteAndPath())) {
					inst.setAccessUrl(RouteUtils.formatAccessURL(v.getTarget().getProtocol(), v.getTarget().getOriginalTarget().getOverrideRouteAndPath(),
							v.getTarget().getPath()));
				}
				else if (v.isInternal()) {
					inst.setAccessUrl(RouteUtils.formatInternalAccessURL(v.getAccessURL(), v.getTarget().getPath(), this.defaultInternalRoutePort,
							v.getInternalRoutePort(), i));
				} else {
					inst.setAccessUrl(RouteUtils.formatAccessURL(v.getTarget().getProtocol(), v.getAccessURL(),
							v.getTarget().getPath()));
				}

				instances.add(inst);
			}

			return Flux.fromIterable(instances);
		});
		
		// perform pre-filtering, if available
		if (instanceFilter != null) {
			instancesFlux = instancesFlux.filter(instanceFilter);
		}
		
		Mono<List<Instance>> listInstancesMono = numberInstancesOSAVectorFlux.ignoreElements().and(internalFlagOSAVectorFlux.ignoreElements())
				.thenMany(instancesFlux).collectList();

		List<Instance> result = null;
		try {
			result = listInstancesMono.block();
		} catch (RuntimeException e) {
			log.error("Error during retrieving the instances of a list of targets", e);
			result = null;
		}

		return result;
	}

}
