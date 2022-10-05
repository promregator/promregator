package org.cloudfoundry.promregator.scanner;

import java.util.List;
import java.util.function.Predicate;

import org.cloudfoundry.client.v3.applications.ListApplicationProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
	
	@Override
	public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets,
			Predicate<? super String> applicationIdFilter, 
			Predicate<? super Instance> instanceFilter) {
		
		Flux<OSAVector> initialOSAVectorFlux = Flux.fromStream(targets.stream().filter(t -> applicationIdFilter.test(t.getApplicationId())))
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
		 * Sending a request for each app to the CFCC would be devastating in terms
		 * of performance.
		 * That is why we try to aggregate
		 * 
		 */
		return null;
	}

}
