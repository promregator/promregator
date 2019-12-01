package org.cloudfoundry.promregator.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.Null;

import org.apache.log4j.Logger;
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
	private static final String INVALID_SPACE_ID = "***invalid***";
	private static final Map<String, SpaceApplicationSummary> INVALID_SUMMARY = new HashMap<>();

	@Value("${cf.cache.timeout.application:300}")
	private int timeoutCacheApplicationLevel;

	/*
	 * see also https://github.com/promregator/promregator/issues/76
	 * This is the locale, which we use to convert both "what we get from CF" and "the stuff, which we get 
	 * from the configuration" into lower case before we try to match them.
	 * 
	 * Note that this might be wrong, if someone might have an app(/org/space) name in Turkish and expects a
	 * Turkish case conversion.
	 */
	private static final Locale LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON = Locale.ENGLISH;

	private static class OSAVector {
		private ResolvedTarget target;
		
		private String orgId;
		private String spaceId;
		private String applicationId;
		
		private String accessURL;
		private int numberOfInstances;
		/**
		 * @return the target
		 */
		public ResolvedTarget getTarget() {
			return target;
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
	public List<Instance> determineInstancesFromTargets(List<ResolvedTarget> targets, @Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter) {
		Flux<ResolvedTarget> targetsFlux = Flux.fromIterable(targets);
		
		Flux<OSAVector> initialOSAVectorFlux = targetsFlux.map(target -> {
			OSAVector v = new OSAVector();
			v.setTarget(target);
			
			return v;
		});
		
		Flux<String> orgIdFlux = initialOSAVectorFlux.flatMapSequential(v -> this.getOrgId(v.getTarget().getOrgName()));
		Flux<OSAVector> OSAVectorOrgFlux = Flux.zip(initialOSAVectorFlux, orgIdFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			if (INVALID_ORG_ID.equals(tuple.getT2())) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			v.setOrgId(tuple.getT2());
			return Mono.just(v);
		});
		
		Flux<String> spaceIdFlux = OSAVectorOrgFlux.flatMapSequential(v -> this.getSpaceId(v.getOrgId(), v.getTarget().getSpaceName()));
		Flux<OSAVector> OSAVectorSpaceFlux = Flux.zip(OSAVectorOrgFlux, spaceIdFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			if (INVALID_SPACE_ID.equals(tuple.getT2())) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			v.setSpaceId(tuple.getT2());
			return Mono.just(v);
		});
		
		Flux<Map<String, SpaceApplicationSummary>> spaceSummaryFlux = OSAVectorSpaceFlux.flatMapSequential(v -> this.getSpaceSummary(v.getSpaceId()));
		Flux<OSAVector> OSAVectorApplicationFlux = Flux.zip(OSAVectorSpaceFlux, spaceSummaryFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			
			if (INVALID_SUMMARY == tuple.getT2()) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			
			Map<String, SpaceApplicationSummary> spaceSummaryMap = tuple.getT2();
			SpaceApplicationSummary sas = spaceSummaryMap.get(v.getTarget().getApplicationName().toLowerCase(LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON));
			
			if (sas == null) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			
			v.setApplicationId(sas.getId());
			
			List<String> urls = sas.getUrls();
			if (urls != null && !urls.isEmpty()) {
				v.setAccessURL(this.determineAccessURL(v.getTarget().getProtocol(), urls, v.getTarget().getOriginalTarget().getPreferredRouteRegexPatterns(), v.getTarget().getPath()));
			}
			
			v.setNumberOfInstances(sas.getInstances());
			
			return Mono.just(v);
		});
		
		// perform pre-filtering, if available
		if (applicationIdFilter != null) {
			OSAVectorApplicationFlux = OSAVectorApplicationFlux.filter(v -> applicationIdFilter.test(v.getApplicationId()));
		}
		
		Flux<Instance> instancesFlux = OSAVectorApplicationFlux.flatMapSequential(v -> {
			List<Instance> instances = new ArrayList<>(v.getNumberOfInstances());
			for (int i = 0; i<v.numberOfInstances; i++) {
				Instance inst = new Instance(v.getTarget(), String.format("%s:%d", v.getApplicationId(), i), v.getAccessURL());
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
		}).onErrorResume(e -> {
			log.error(String.format("retrieving Org Id for org Name '%s' resulted in an exception", orgNameString), e);
			return Mono.just(INVALID_ORG_ID);
		}).cache();
		
		return orgId;
	}

	private Mono<String> getSpaceId(String orgIdString, String spaceNameString) {

		Mono<ListSpacesResponse> listSpacesResponse = this.cfAccessor.retrieveSpaceId(orgIdString, spaceNameString);

		Mono<String> spaceId = listSpacesResponse.flatMap(response -> {
			List<SpaceResource> resources = response.getResources();
			if (resources == null) {
				return Mono.just(INVALID_SPACE_ID);
			}
			
			if (resources.isEmpty()) {
				log.warn(String.format("Received empty result on requesting space %s", spaceNameString));
				return Mono.just(INVALID_SPACE_ID);
			}
			
			SpaceResource spaceResource = resources.get(0);
			return Mono.just(spaceResource.getMetadata().getId());
		}).onErrorResume(e -> {
			log.error(String.format("retrieving space id for org id '%s' and space name '%s' resulted in an exception", orgIdString, spaceNameString), e);
			return Mono.just(INVALID_SPACE_ID);
		}).cache();
		
		return spaceId;
	}
	
	private Mono<Map<String, SpaceApplicationSummary>> getSpaceSummary(String spaceIdString) {
		return this.cfAccessor.retrieveSpaceSummary(spaceIdString)
			.flatMap(response -> {
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
	
	private String determineAccessURL(final String protocol, final List<String> urls, final List<Pattern> preferredRouteRegex, final String path) {
		
		final String url = determineApplicationRoute(urls, preferredRouteRegex);
		final String applicationUrl = String.format("%s://%s", protocol, url);
		log.debug (String.format("Using Application URL: '%s'", applicationUrl));
		
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

	private String determineApplicationRoute(final List<String> urls, final List<Pattern> patterns) {
		if (urls == null || urls.isEmpty()) {
			log.debug("No URLs provided to determine ApplicationURL with");
			return null;
		}

		if (patterns == null || patterns.isEmpty()) {
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
		
		// if we reach this here, then we did not find any match in the regex.
		// The fallback then is the old behavior by returned just the first-guess element
		log.debug(String.format("Though Preferred Router URLs were provided, no route matched; taking the first route as fallback (compatibility!), which is '%s'", urls.get(0))); 
		return urls.get(0);
	}
}
