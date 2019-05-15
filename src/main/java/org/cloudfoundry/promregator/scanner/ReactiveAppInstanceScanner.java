package org.cloudfoundry.promregator.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Null;

import org.apache.commons.collections4.map.PassiveExpiringMap;
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

	private PassiveExpiringMap<String, Mono<String>> applicationUrlMap;
	
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
		Flux<OSAVector> OSAVectorSpaceFlux = Flux.zip(OSAVectorOrgFlux, spaceIdFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			if (INVALID_SPACE_ID.equals(tuple.getT2())) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			v.spaceId = tuple.getT2();
			return Mono.just(v);
		});
		
		Flux<Map<String, SpaceApplicationSummary>> spaceSummaryFlux = OSAVectorSpaceFlux.flatMapSequential(v -> this.getSpaceSummary(v.spaceId));
		Flux<OSAVector> OSAVectorApplicationFlux = Flux.zip(OSAVectorSpaceFlux, spaceSummaryFlux).flatMap(tuple -> {
			OSAVector v = tuple.getT1();
			
			if (INVALID_SUMMARY == tuple.getT2()) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			
			Map<String, SpaceApplicationSummary> spaceSummaryMap = tuple.getT2();
			SpaceApplicationSummary sas = spaceSummaryMap.get(v.target.getApplicationName().toLowerCase(LOCALE_OF_LOWER_CASE_CONVERSION_FOR_IDENTIFIER_COMPARISON));
			
			if (sas == null) {
				// NB: This drops the current target!
				return Mono.empty();
			}
			
			v.applicationId = sas.getId();
			
			List<String> urls = sas.getUrls();
			if (urls != null && !urls.isEmpty()) {
				v.accessURL = this.determineAccessURL(v.target.getProtocol(), urls, v.target.getOriginalTarget().getPreferredRouteRegexPatterns(), v.target.getPath());
			}
			
			v.numberOfInstances = sas.getInstances();
			
			return Mono.just(v);
		});
		
		// perform pre-filtering, if available
		if (applicationIdFilter != null) {
			OSAVectorApplicationFlux = OSAVectorApplicationFlux.filter(v -> applicationIdFilter.test(v.applicationId));
		}
		
		Flux<Instance> instancesFlux = OSAVectorApplicationFlux.flatMapSequential(v -> {
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
		
		final String url = determineApplicationUrl(urls, preferredRouteRegex);
		final String applicationUrl = String.format("%s://%s", protocol, url);
		
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

	private String determineApplicationUrl(final List<String> urls, final List<Pattern> patterns) {
		if (urls == null || urls.size() == 0) {
			return null;
		}

		if (patterns == null || patterns.size() == 0) {
			return urls.get(0);
		}
		
		for (Pattern pattern : patterns) {
			for (String url : urls) {
				Matcher m = pattern.matcher(url);
				if (m.matches()) {
					return url;
				}
			}
		}
		
		// if we reach this here, then we did not find any match in the regex.
		// The fallback then is the old behavior by returned just the first-guess element
		return urls.get(0);
	}
	
	public void invalidateApplicationUrlCache() {
		this.applicationUrlMap.clear();
	}
}
