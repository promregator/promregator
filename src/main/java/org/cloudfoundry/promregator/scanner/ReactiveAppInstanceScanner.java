package org.cloudfoundry.promregator.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.logging.log4j.util.Strings;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudfoundry.client.v3.applications.ListApplicationProcessesResponse;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.routes.ListRoutesResponse;
import org.cloudfoundry.client.v3.routes.RouteResource;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveAppInstanceScanner implements AppInstanceScanner {

	private static final Logger log = LoggerFactory.getLogger(ReactiveAppInstanceScanner.class);
	private static final String INVALID_ORG_ID = "***invalid***";
	private static final String INVALID_SPACE_ID = "***invalid***";

	@Value("${promregator.defaultInternalRoutePort:8080}")
	private int defaultInternalRoutePort;

	/**
	 * OSA stands for Org-Space-Application
	 */
	private static class OSAVector {
		private ResolvedTarget target;

		private String orgId;
		private String spaceId;
		private String applicationId;
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
		 * @return the orgId
		 */
		public String getOrgId() {
			return orgId;
		}

		/**
		 * @param orgId the orgId to set
		 */
		public void setOrgId(String orgId) {
			this.orgId = orgId;
		}

		/**
		 * @return the spaceId
		 */
		public String getSpaceId() {
			return spaceId;
		}

		/**
		 * @param spaceId the spaceId to set
		 */
		public void setSpaceId(String spaceId) {
			this.spaceId = spaceId;
		}

		/**
		 * @return the applicationId
		 */
		public String getApplicationId() {
			return applicationId;
		}

		/**
		 * @param applicationId the applicationId to set
		 */
		public void setApplicationId(String applicationId) {
			this.applicationId = applicationId;
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
			@Nullable Predicate<? super String> applicationIdFilter,
			@Nullable Predicate<? super Instance> instanceFilter) {
		Flux<ResolvedTarget> targetsFlux = Flux.fromIterable(targets);

		/*
		 * TODO V3: Check, if this serial approach below can be executed more in parallel
		 */
		
		Flux<OSAVector> initialOSAVectorFlux = targetsFlux.filter(rt -> rt.getApplicationId() != null)
			.map(target -> {
				OSAVector v = new OSAVector();
				v.setTarget(target);
				v.setApplicationId(target.getApplicationId());
				v.setInternalRoutePort(target.getOriginalTarget().getInternalRoutePort());
				return v;
			});

		Flux<String> orgIdFlux = initialOSAVectorFlux.flatMapSequential(v -> this.getOrgId(v.getTarget().getOrgName()));
		Flux<OSAVector> osaVectorOrgFlux = Flux.zip(initialOSAVectorFlux, orgIdFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			if (INVALID_ORG_ID.equals(tuple.getT2())) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			v.setOrgId(tuple.getT2());
			return Mono.just(v);
		});

		Flux<String> spaceIdFlux = osaVectorOrgFlux.flatMapSequential(v -> this.getSpaceId(v.getOrgId(), v.getTarget().getSpaceName()));
		Flux<OSAVector> osaVectorSpaceFlux = Flux.zip(osaVectorOrgFlux, spaceIdFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			if (INVALID_SPACE_ID.equals(tuple.getT2())) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			v.setSpaceId(tuple.getT2());
			return Mono.just(v);
		});

		/*
		 * For V3 it is no longer possible to get the SpaceSummary.
		 * This implies that we need to retrieve data on application level :-(
		 * Instead, the instance count can be found at the Processes endpoint.
		 * The ApplicationURL is buried in the Routes.
		 */
		
		Flux<ListApplicationProcessesResponse> webProcessForAppFlux = osaVectorSpaceFlux.flatMap(rt -> this.cfAccessor.retrieveWebProcessesForApp(rt.getTarget().getApplicationId()));
		
		Flux<OSAVector> numberInstancesOSAVectorFlux = Flux.zip(osaVectorSpaceFlux, webProcessForAppFlux).flatMap(tuple -> {
			final OSAVector osaVector = tuple.getT1();
			final ResolvedTarget rt = osaVector.getTarget();
			final ListApplicationProcessesResponse lapr = tuple.getT2();
			
			List<ProcessResource> list = lapr.getResources();
			if (list.size() > 1) {
				log.error(String.format("Application Id %s with application name %s in org %s and space %s returned multiple web processes via CF API V3 Processes; Promregator does not know how to handle this. Provide your use case to the developers to understand how this shall be handled properly.", rt.getApplicationId(), rt.getApplicationName(), rt.getOrgName(), rt.getSpaceName()));
				return Mono.empty();
			}
			
			if (list.isEmpty()) {
				log.error(String.format("Application Id %s with application name %s in org %s and space %s returned no web processes via CF API V3 Processes; Promregator does not know how to handle this. Provide your use case to the developers to understand how this shall be handled properly.", rt.getApplicationId(), rt.getApplicationName(), rt.getOrgName(), rt.getSpaceName()));
				return Mono.empty();
			}
			
			ProcessResource pr = list.get(0);
			final int numberInstances = pr.getInstances();
			osaVector.setNumberOfInstances(numberInstances);
			return Mono.just(osaVector);
		});
		
		Flux<ListRoutesResponse> routesForAppFlux = numberInstancesOSAVectorFlux.flatMap(rt -> this.cfAccessor.retrieveRoutesForAppId(rt.getTarget().getApplicationId()));
		Flux<OSAVector> urlDomainOSAVectorFlux = Flux.zip(numberInstancesOSAVectorFlux, routesForAppFlux).flatMap(tuple -> {
			final OSAVector osaVector = tuple.getT1();
			final ListRoutesResponse lrp = tuple.getT2();
			
			final List<RouteResource> list = lrp.getResources();
			if (list == null || list.isEmpty()) {
				// no route defined; the target cannot be reached anyway
				return Mono.empty();
			}
			
			final List<String> urls = list.stream().map(RouteResource::getUrl).toList();
			@NonNull
			final List<Pattern> preferredRouteRegexPatterns = osaVector.getTarget().getOriginalTarget().getPreferredRouteRegexPatterns();
			final String url = this.determineApplicationRoute(urls, preferredRouteRegexPatterns);
			
			if (url == null) {
				// no suitable route found
				return Mono.empty();
			}
			
			osaVector.setAccessURL(url);
			
			// determine domain
			final RouteResource selectedRouteResource = list.stream().filter(e -> e.getUrl().equals(url)).findFirst().get();
			final String domainId = selectedRouteResource.getRelationships().getDomain().getData().getId();
			osaVector.setDomainId(domainId);
			
			return Mono.just(osaVector);
		});
		

		Flux<List<DomainResource>> domainFlux = urlDomainOSAVectorFlux.flatMapSequential(v -> this.cfAccessor.retrieveAllDomainsV3(v.getOrgId()).map(ListOrganizationDomainsResponse::getResources));
		Flux<OSAVector> osaVectorDomainApplicationFlux = Flux.zip(urlDomainOSAVectorFlux, domainFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			List<DomainResource> domains = tuple.getT2();

			if (domains.size() == 0 || v.getDomainId() == null) {
				// NB: This drops the current target!
				return Mono.empty();
			}

			if (useOverrideRouteAndPath(v)) {
				v.setInternal(true);
			}
			// we should only run this if we found a domain in the above step
			// this is to make sure we have compatibility with existing behaviour
			else if (!v.getDomainId().isEmpty()) {
				try {
					DomainResource domain = domains.stream()
							.filter(r -> r.getId().equals(v.getDomainId()))
							.findFirst()
							.get();
					
					v.setInternal(domain.isInternal());
				} catch (Exception e) {
					log.warn(String.format("unable to find matching domain for the domain with id %s", v.getDomainId()));
				}
			}

			return Mono.just(v);
		});


		// perform pre-filtering, if available
		if (applicationIdFilter != null) {
			osaVectorDomainApplicationFlux = osaVectorDomainApplicationFlux.filter(v -> applicationIdFilter.test(v.getApplicationId()));
		}

		Flux<Instance> instancesFlux = osaVectorDomainApplicationFlux.flatMapSequential(v -> {
			List<Instance> instances = new ArrayList<>(v.getNumberOfInstances());
			for (int i = 0; i < v.numberOfInstances; i++) {
				Instance inst = new Instance(v.getTarget(), String.format("%s:%d", v.getApplicationId(), i), v.getAccessURL(), v.isInternal());

				if (useOverrideRouteAndPath(v)) {
					inst.setAccessUrl(this.formatAccessURL(v.getTarget().getProtocol(), v.getTarget().getOriginalTarget().getOverrideRouteAndPath(), v.getTarget().getPath()));
				}
				else if (v.isInternal()) {
					inst.setAccessUrl(this.formatInternalAccessURL(v.getAccessURL(), v.getTarget().getPath(), v.getInternalRoutePort(), i));
				} else {
					inst.setAccessUrl(this.formatAccessURL(v.getTarget().getProtocol(), v.getAccessURL(), v.getTarget().getPath()));
				}

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

	private boolean useOverrideRouteAndPath(OSAVector v) {
		return Strings.isNotEmpty(v.getTarget().getOriginalTarget().getOverrideRouteAndPath());
	}

	private Mono<String> getOrgId(String orgNameString) {
		return this.cfAccessor.retrieveOrgIdV3(orgNameString).flatMap(response -> {
			List<org.cloudfoundry.client.v3.organizations.OrganizationResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_ORG_ID);
			}

			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting org %s", orgNameString));
				return Mono.just(INVALID_ORG_ID);
			}

			org.cloudfoundry.client.v3.organizations.OrganizationResource organizationResource = resources.get(0);
			return Mono.just(organizationResource.getId());
		}).onErrorResume(e -> {
			log.error(String.format("retrieving Org Id for org Name '%s' resulted in an exception", orgNameString), e);
			return Mono.just(INVALID_ORG_ID);
		}).cache();
	}

	private Mono<String> getSpaceId(String orgIdString, String spaceNameString) {

		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> listSpacesResponse = this.cfAccessor.retrieveSpaceIdV3(orgIdString, spaceNameString);

		return listSpacesResponse.flatMap(response -> {
			List<org.cloudfoundry.client.v3.spaces.SpaceResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_SPACE_ID);
			}

			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting space %s", spaceNameString));
				return Mono.just(INVALID_SPACE_ID);
			}

			org.cloudfoundry.client.v3.spaces.SpaceResource spaceResource = resources.get(0);
			return Mono.just(spaceResource.getId());
		}).onErrorResume(e -> {
			log.error(String.format("retrieving space id for org id '%s' and space name '%s' resulted in an exception",
					orgIdString, spaceNameString), e);
			return Mono.just(INVALID_SPACE_ID);
		}).cache();

	}

	private String formatAccessURL(final String protocol, final String hostnameDomain, final String path) {
		final String applicationUrl = String.format("%s://%s", protocol, hostnameDomain);
		log.debug(String.format("Using Application URL: '%s'", applicationUrl));

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

	private String formatInternalAccessURL(final String hostnameDomain, final String path, final int internalRoutePort, final int instanceId) {
		int port = internalRoutePort;
		if(port == 0) {
			port = defaultInternalRoutePort;
		}
		
		String internalURL = String.format("%s.%s:%s", instanceId, hostnameDomain, port);
		log.debug(String.format("Using internal Application URL: '%s'", internalURL));

		return formatAccessURL("http", internalURL, path);
	}

	private String determineApplicationRoute(final List<String> urls, final List<Pattern> patterns) {
		if (urls == null || urls.isEmpty()) {
			log.debug("No URLs provided to determine ApplicationURL with");
			return null;
		}

		if (CollectionUtils.isEmpty(patterns)) {
			log.debug("No Preferred Route URL (Regex) provided; taking first Application Route in the list provided");
			return urls.get(0);
		}

		for (Pattern pattern : patterns) {
			for (String url : urls) {
				log.debug(String.format("Attempting to match Application Route '%s' against pattern '%s'", url, pattern.toString()));
				Matcher m = pattern.matcher(url);
				if (m.matches()) {
					log.debug(String.format("Match found, using Application Route '%s'", url));
					return url;
				}
			}
		}

		/* 
		 * If we reach this here, then we did not find any match in the regex.
		 * The fallback then is the old behavior by returned just the first-guess
		 * element.
		 */
		log.debug(String.format("Though Preferred Router URLs were provided, no route matched; taking the first route as fallback (compatibility!), which is '%s'", urls.get(0)));
		return urls.get(0);
	}
}
