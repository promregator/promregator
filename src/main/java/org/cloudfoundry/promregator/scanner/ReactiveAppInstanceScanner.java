package org.cloudfoundry.promregator.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.logging.log4j.util.Strings;
import org.cloudfoundry.client.v2.domains.DomainResource;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.routes.Route;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceApplicationSummary;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
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
	private static final Map<String, SpaceApplicationSummary> INVALID_SUMMARY = new HashMap<>();

	@Value("${promregator.defaultInternalRoutePort:8080}")
	private int defaultInternalRoutePort;

	/*
	 * see also https://github.com/promregator/promregator/issues/76 This is the
	 * locale, which we use to convert both "what we get from CF" and "the stuff,
	 * which we get from the configuration" into lower case before we try to match
	 * them.
	 * 
	 * Note that this might be wrong, if someone might have an app(/org/space) name
	 * in Turkish and expects a Turkish case conversion.
	 */
	private static final Locale LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON = Locale.ENGLISH;

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

		Flux<OSAVector> initialOSAVectorFlux = targetsFlux.map(target -> {
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

		Flux<String> spaceIdFlux = osaVectorOrgFlux
				.flatMapSequential(v -> this.getSpaceId(v.getOrgId(), v.getTarget().getSpaceName()));
		Flux<OSAVector> osaVectorSpaceFlux = Flux.zip(osaVectorOrgFlux, spaceIdFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			if (INVALID_SPACE_ID.equals(tuple.getT2())) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			v.setSpaceId(tuple.getT2());
			return Mono.just(v);
		});

		Flux<Map<String, SpaceApplicationSummary>> spaceSummaryFlux = osaVectorSpaceFlux
				.flatMapSequential(v -> this.getSpaceSummary(v.getSpaceId()));
		Flux<OSAVector> osaVectorApplicationFlux = Flux.zip(osaVectorSpaceFlux, spaceSummaryFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();

			if (INVALID_SUMMARY == tuple.getT2()) {
				// NB: This drops the current target!
				return Mono.empty();
			}

			Map<String, SpaceApplicationSummary> spaceSummaryMap = tuple.getT2();
			SpaceApplicationSummary sas = spaceSummaryMap.get(v.getTarget().getApplicationName()
					.toLowerCase(LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON));
			/*
			 * Due to https://github.com/cloudfoundry/cloud_controller_ng/issues/1523, we
			 * cannot rely on sas.getId() (i.e. it may contain wrong information)
			 */

			if (sas == null) {
				// NB: This drops the current target!
				return Mono.empty();
			}

			List<String> urls = sas.getUrls();
			if (urls != null && !urls.isEmpty()) {
				// Set the access url to the selected route (without any protocol or path yet)
				v.setAccessURL(this.determineApplicationRoute(urls,
						v.getTarget().getOriginalTarget().getPreferredRouteRegexPatterns()));
			} else {
				// if there is no url, skip this one
				return Mono.empty();
			}

			// In the interest of backwards compatibility lest do this inside a try.
			// There is little reason why this should not find the correct domain
			try {
				Route route = sas.getRoutes().stream().filter(rt -> v.getAccessURL().startsWith(rt.getHost()+"."+rt.getDomain().getName())).findFirst().get();
				v.setDomainId(route.getDomain().getId());
			} catch (Exception e) {
				log.warn(String.format("unable to find matching domain for the url %s", v.getAccessURL()));
			}
			
			v.setNumberOfInstances(sas.getInstances());

			return Mono.just(v);
		});

		Flux<List<DomainResource>> domainFlux = osaVectorApplicationFlux.flatMapSequential(v -> {
			return this.cfAccessor.retrieveAllDomains(v.getOrgId()).map(mapper -> mapper.getResources());
		});
		Flux<OSAVector> osaVectorDomainApplicationFlux = Flux.zip(osaVectorApplicationFlux, domainFlux).flatMap(tuple -> {
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
			else if( !v.getDomainId().isEmpty()) {
				try {
					DomainResource domain = domains.stream().filter(r -> r.getMetadata().getId().equals(v.getDomainId()))
						.findFirst().get();
					v.setInternal(domain.getEntity().getInternal());
				} catch (Exception e) {
					log.warn(String.format("unable to find matching domain for the domain with id %s", v.getDomainId()));
				}
			}

			return Mono.just(v);
		});


		// perform pre-filtering, if available
		if (applicationIdFilter != null) {
			osaVectorDomainApplicationFlux = osaVectorDomainApplicationFlux
					.filter(v -> applicationIdFilter.test(v.getApplicationId()));
		}

		Flux<Instance> instancesFlux = osaVectorDomainApplicationFlux.flatMapSequential(v -> {
			List<Instance> instances = new ArrayList<>(v.getNumberOfInstances());
			for (int i = 0; i < v.numberOfInstances; i++) {
				Instance inst = new Instance(v.getTarget(), String.format("%s:%d", v.getApplicationId(), i),
						v.getAccessURL(), v.isInternal());

				if(useOverrideRouteAndPath(v)) {
					inst.setAccessUrl(this.formatAccessURL(v.getTarget().getProtocol(), v.getTarget().getOriginalTarget().getOverrideRouteAndPath(),
							v.getTarget().getPath()));
				}
				else if (v.isInternal()) {
					inst.setAccessUrl(this.formatInternalAccessURL(v.getAccessURL(), v.getTarget().getPath(),
							v.getInternalRoutePort(), i));
				} else {
					inst.setAccessUrl(this.formatAccessURL(v.getTarget().getProtocol(), v.getAccessURL(),
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
		return this.cfAccessor.isV3Enabled() ? this.getOrgIdV3(orgNameString) : this.getOrgIdV2(orgNameString);
	}
	
	private Mono<String> getOrgIdV2(String orgNameString) {
		return this.cfAccessor.retrieveOrgId(orgNameString).flatMap(response -> {
			List<OrganizationResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_ORG_ID);
			}

			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting org %s using CF API V2", orgNameString));
				return Mono.just(INVALID_ORG_ID);
			}

			OrganizationResource organizationResource = resources.get(0);
			return Mono.just(organizationResource.getMetadata().getId());
		}).onErrorResume(e -> {
			log.error(String.format("retrieving Org Id for org Name '%s' via CF API V2 resulted in an exception", orgNameString), e);
			return Mono.just(INVALID_ORG_ID);
		}).cache();
	}
	
	private Mono<String> getOrgIdV3(String orgNameString) {
		return this.cfAccessor.retrieveOrgIdV3(orgNameString).flatMap(response -> {
			List<org.cloudfoundry.client.v3.organizations.OrganizationResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_ORG_ID);
			}

			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting org %s using CF API V3", orgNameString));
				return Mono.just(INVALID_ORG_ID);
			}

			org.cloudfoundry.client.v3.organizations.OrganizationResource organizationResource = resources.get(0);
			return Mono.just(organizationResource.getId());
		}).onErrorResume(e -> {
			log.error(String.format("retrieving Org Id for org Name '%s' via CF API V3 resulted in an exception", orgNameString), e);
			return Mono.just(INVALID_ORG_ID);
		}).cache();
	}

	private Mono<String> getSpaceId(String orgIdString, String spaceNameString) {
		return this.cfAccessor.isV3Enabled() ? this.getSpaceIdV3(orgIdString, spaceNameString) : this.getSpaceIdV2(orgIdString, spaceNameString);
	}
	
	private Mono<String> getSpaceIdV2(String orgIdString, String spaceNameString) {

		Mono<ListSpacesResponse> listSpacesResponse = this.cfAccessor.retrieveSpaceId(orgIdString, spaceNameString);

		return listSpacesResponse.flatMap(response -> {
			List<SpaceResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_SPACE_ID);
			}

			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting space %s using CF API V2", spaceNameString));
				return Mono.just(INVALID_SPACE_ID);
			}

			SpaceResource spaceResource = resources.get(0);
			return Mono.just(spaceResource.getMetadata().getId());
		}).onErrorResume(e -> {
			log.error(String.format("retrieving space id for org id '%s' and space name '%s' via CF API V2 resulted in an exception",
					orgIdString, spaceNameString), e);
			return Mono.just(INVALID_SPACE_ID);
		}).cache();

	}
	
	private Mono<String> getSpaceIdV3(String orgIdString, String spaceNameString) {
		
		Mono<ListSpacesResponse> listSpacesResponse = this.cfAccessor.retrieveSpaceId(orgIdString, spaceNameString);
		
		return listSpacesResponse.flatMap(response -> {
			List<SpaceResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_SPACE_ID);
			}
			
			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting space %s using CF API V3", spaceNameString));
				return Mono.just(INVALID_SPACE_ID);
			}
			
			SpaceResource spaceResource = resources.get(0);
			return Mono.just(spaceResource.getMetadata().getId());
		}).onErrorResume(e -> {
			log.error(String.format("retrieving space id for org id '%s' and space name '%s' via CF API V3 resulted in an exception",
					orgIdString, spaceNameString), e);
			return Mono.just(INVALID_SPACE_ID);
		}).cache();
		
	}

	private Mono<Map<String, SpaceApplicationSummary>> getSpaceSummary(String spaceIdString) {
		return this.cfAccessor.retrieveSpaceSummary(spaceIdString).flatMap(response -> {
			List<SpaceApplicationSummary> applications = response.getApplications();
			if (applications == null) {
				return Mono.just(INVALID_SUMMARY);
			}

			Map<String, SpaceApplicationSummary> map = new HashMap<>(applications.size());
			for (SpaceApplicationSummary sas : applications) {
				map.put(sas.getName().toLowerCase(LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON), sas);
			}

			return Mono.just(map);
		}).onErrorResume(e -> {
			log.error(String.format("retrieving summary for space id '%s' resulted in an exception", spaceIdString), e);
			return Mono.just(INVALID_SUMMARY);
		});
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

	private String formatInternalAccessURL(final String hostnameDomain, final String path, final int internalRoutePort,
			final int instanceId) {
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
				log.debug(String.format("Attempting to match Application Route '%s' against pattern '%s'", url,
						pattern.toString()));
				Matcher m = pattern.matcher(url);
				if (m.matches()) {
					log.debug(String.format("Match found, using Application Route '%s'", url));
					return url;
				}
			}
		}

		// if we reach this here, then we did not find any match in the regex.
		// The fallback then is the old behavior by returned just the first-guess
		// element
		log.debug(String.format(
				"Though Preferred Router URLs were provided, no route matched; taking the first route as fallback (compatibility!), which is '%s'",
				urls.get(0)));
		return urls.get(0);
	}
}
