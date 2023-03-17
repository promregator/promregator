package org.cloudfoundry.promregator.scanner;

import static org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl.INVALID_APPLICATIONS_RESPONSE;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ApplicationState;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ReactiveTargetResolver implements TargetResolver {
	private static final Logger log = LoggerFactory.getLogger(ReactiveTargetResolver.class);
	private static final Logger logEmptyTarget = LoggerFactory.getLogger(String.format("%s.EmptyTarget", ReactiveTargetResolver.class.getName()));
	public static final String PROMETHEUS_IO_SCRAPE = "prometheus.io/scrape";
	public static final String PROMETHEUS_IO_PATH = "prometheus.io/path";

	@Autowired
	private CFAccessor cfAccessor;

	private static class IntermediateTarget {
		private Target configTarget;
		private String resolvedOrgName;
		private String resolvedOrgId;
		private String resolvedSpaceName;
		private String resolvedSpaceId;
		private String resolvedApplicationName;
		private String resolvedApplicationId;
		private String resolvedMetricsPath;
		
		public IntermediateTarget() {
			super();
		}
		
		public IntermediateTarget(IntermediateTarget source) {
			this.configTarget = source.configTarget;
			this.resolvedOrgName = source.resolvedOrgName;
			this.resolvedOrgId = source.resolvedOrgId;
			this.resolvedSpaceName = source.resolvedSpaceName;
			this.resolvedSpaceId = source.resolvedSpaceId;
			this.resolvedApplicationName = source.resolvedApplicationName;
			this.resolvedApplicationId = source.resolvedApplicationId;
			this.resolvedMetricsPath = source.resolvedMetricsPath;
		}

		public IntermediateTarget(Target target) {
			this.configTarget = target;
		}

		public ResolvedTarget toResolvedTarget() {
			if (getResolvedOrgId() == null) {
				log.error("Target '{}' created a ResolvedTarget without resolved org id", getConfigTarget());
			}

			if (getResolvedSpaceId() == null) {
				log.error("Target '{}' created a ResolvedTarget without resolved space id", getConfigTarget());
			}

			if (getResolvedApplicationId() == null) {
				log.error("Target '{}' created a ResolvedTarget without resolved application id", getConfigTarget());
			}

			if (getResolvedOrgName() == null) {
				log.error("Target '{}' created a ResolvedTarget without resolved org name", getConfigTarget());
			}

			if (getResolvedSpaceName() == null) {
				log.error("Target '{}' created a ResolvedTarget without resolved space name", getConfigTarget());
			}

			if (getResolvedApplicationName() == null) {
				log.error("Target '{}' created a ResolvedTarget without resolved application name", getConfigTarget());
			}


			ResolvedTarget rt = new ResolvedTarget();

			rt.setOriginalTarget(this.configTarget);
			rt.setOrgName(this.resolvedOrgName);
			rt.setSpaceName(this.resolvedSpaceName);
			rt.setApplicationName(this.resolvedApplicationName);
			rt.setProtocol(this.configTarget.getProtocol());
			rt.setPath(this.resolvedMetricsPath != null ? this.resolvedMetricsPath : this.configTarget.getPath());
			rt.setApplicationId(this.resolvedApplicationId);
			return rt;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "IntermediateTarget [configTarget=" + configTarget + ", resolvedOrgName=" + resolvedOrgName
					+ ", resolvedOrgId=" + resolvedOrgId + ", resolvedSpaceName=" + resolvedSpaceName
					+ ", resolvedSpaceId=" + resolvedSpaceId + ", resolvedApplicationName=" + resolvedApplicationName
					+ ", resolvedApplicationId=" + resolvedApplicationId + "]";
		}

		/**
		 * @return the configTarget
		 */
		public Target getConfigTarget() {
			return configTarget;
		}

		/**
		 * @param configTarget the configTarget to set
		 */
		public void setConfigTarget(Target configTarget) {
			this.configTarget = configTarget;
		}

		/**
		 * @return the resolvedOrgId
		 */
		public String getResolvedOrgId() {
			return resolvedOrgId;
		}

		/**
		 * @param resolvedOrgId the resolvedOrgId to set
		 */
		public void setResolvedOrgId(String resolvedOrgId) {
			this.resolvedOrgId = resolvedOrgId;
		}

		/**
		 * @return the resolvedOrgName
		 */
		public String getResolvedOrgName() {
			return resolvedOrgName;
		}

		/**
		 * @param resolvedOrgName the resolvedOrgName to set
		 */
		public void setResolvedOrgName(String resolvedOrgName) {
			this.resolvedOrgName = resolvedOrgName;
		}

		/**
		 * @return the resolvedSpaceName
		 */
		public String getResolvedSpaceName() {
			return resolvedSpaceName;
		}

		/**
		 * @param resolvedSpaceName the resolvedSpaceName to set
		 */
		public void setResolvedSpaceName(String resolvedSpaceName) {
			this.resolvedSpaceName = resolvedSpaceName;
		}

		/**
		 * @return the resolvedSpaceId
		 */
		public String getResolvedSpaceId() {
			return resolvedSpaceId;
		}

		/**
		 * @param resolvedSpaceId the resolvedSpaceId to set
		 */
		public void setResolvedSpaceId(String resolvedSpaceId) {
			this.resolvedSpaceId = resolvedSpaceId;
		}

		/**
		 * @return the resolvedApplicationName
		 */
		public String getResolvedApplicationName() {
			return resolvedApplicationName;
		}

		/**
		 * @param resolvedApplicationName the resolvedApplicationName to set
		 */
		public void setResolvedApplicationName(String resolvedApplicationName) {
			this.resolvedApplicationName = resolvedApplicationName;
		}

		/**
		 * @return the resolvedApplicationId
		 */
		public String getResolvedApplicationId() {
			return resolvedApplicationId;
		}

		/**
		 * @param resolvedApplicationId the resolvedApplicationId to set
		 */
		public void setResolvedApplicationId(String resolvedApplicationId) {
			this.resolvedApplicationId = resolvedApplicationId;
		}

		/**
		 * @return the resolved metrics path
		 */
		public String getResolvedMetricsPath() {
			return resolvedMetricsPath;
		}

		/**
		 * @param resolvedMetricsPath the resolvedMetricsPath to set
		 */
		public void setResolvedMetricsPath(String resolvedMetricsPath) {
			this.resolvedMetricsPath = resolvedMetricsPath;
		}
		
		
	}

	@Override
	public List<ResolvedTarget> resolveTargets(List<Target> configTargets) {
		return Flux.fromIterable(configTargets)
				.parallel()
				.runOn(Schedulers.parallel())
				.map(IntermediateTarget::new)
				.flatMap(this::resolveOrg)
				.log(log.getName() + ".resolveOrg")
				.flatMap(this::resolveSpace)
				.log(log.getName() + ".resolveSpace")
				.flatMap (this::resolveApplication)
				.log(log.getName() + ".resolveApplication")
				.flatMap(this::resolveAnnotations)
				.log(log.getName() + ".resolveAnnotations")
				.map(IntermediateTarget::toResolvedTarget)
				.sequential()
				.distinct().collectList()
				.doOnNext(it -> log.debug("Successfully resolved {} configuration targets to {} resolved targets", configTargets.size(), it.size()))
				.block();
	}

	private Flux<IntermediateTarget> resolveOrg(IntermediateTarget it) {
		/* NB: Now we have to consider three cases:
		 * Case 1: both orgName and orgRegex is empty => select all orgs
		 * Case 2: orgName is null, but orgRegex is filled => filter all orgs with the regex
		 * Case 3: orgName is filled, but orgRegex is null => select a single org
		 * In cases 1 and 2, we need the list of all orgs on the platform.
		 */
		
		if (it.getConfigTarget().getOrgRegex() == null && it.getConfigTarget().getOrgName() != null) {
			// Case 3: we have the orgName, but we also need its id
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveOrgIdV3(it.getConfigTarget().getOrgName())
					.map(org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse::getResources)
					.flatMap(resList -> {
						if (resList == null || resList.isEmpty()) {
							return Mono.empty();
						}
						
						return Mono.just(resList.get(0));
					})
					.map(res -> {
						it.setResolvedOrgName(res.getName());
						it.setResolvedOrgId(res.getId());
						return it;
					})
					.doOnError(e -> log.warn("Error on retrieving org id for org '{}'", it.getConfigTarget().getOrgName(), e))
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all orgs from the platform
		Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> responseMono = this.cfAccessor.retrieveAllOrgIdsV3();

		Flux<org.cloudfoundry.client.v3.organizations.OrganizationResource> orgResFlux = responseMono.map(org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse::getResources)
			.flatMapMany(Flux::fromIterable);
		
		if (it.getConfigTarget().getOrgRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.getConfigTarget().getOrgRegex(), Pattern.CASE_INSENSITIVE);
			
			orgResFlux = orgResFlux.filter(orgRes -> {
				Matcher m = filterPattern.matcher(orgRes.getName());
				return m.matches();
			});
		}
		
		return orgResFlux.map(orgRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.setResolvedOrgId(orgRes.getId());
			itnew.setResolvedOrgName(orgRes.getName());
			
			return itnew;
		});
	}
	
	private Flux<IntermediateTarget> resolveSpace(IntermediateTarget it) {
		/* NB: Now we have to consider three cases:
		 * Case 1: both spaceName and spaceRegex is empty => select all spaces (within the org)
		 * Case 2: spaceName is null, but spaceRegex is filled => filter all spaces with the regex
		 * Case 3: spaceName is filled, but spaceRegex is null => select a single space
		 * In cases 1 and 2, we need the list of all spaces in the org.
		 */
		
		if (it.getConfigTarget().getSpaceRegex() == null && it.getConfigTarget().getSpaceName() != null) {
			// Case 3: we have the spaceName, but we also need its id
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveSpaceIdV3(it.getResolvedOrgId(), it.getConfigTarget().getSpaceName())
					.map(org.cloudfoundry.client.v3.spaces.ListSpacesResponse::getResources)
					.flatMap(resList -> {
						if (resList == null || resList.isEmpty()) {
							return Mono.empty();
						}
						
						return Mono.just(resList.get(0));
					})
					.map(res -> {
						it.setResolvedSpaceName(res.getName());
						it.setResolvedSpaceId(res.getId());
						return it;
					}).doOnError(e -> log.warn("Error on retrieving space id for org '{}' and space '{}'", it.getResolvedOrgName(), it.getConfigTarget().getSpaceName(), e))
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all spaces in the current org
		Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> responseMono = this.cfAccessor.retrieveSpaceIdsInOrgV3(it.getResolvedOrgId());

		Flux<org.cloudfoundry.client.v3.spaces.SpaceResource> spaceResFlux = responseMono.map(org.cloudfoundry.client.v3.spaces.ListSpacesResponse::getResources)
			.flatMapMany(Flux::fromIterable);
		
		if (it.getConfigTarget().getSpaceRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.getConfigTarget().getSpaceRegex(), Pattern.CASE_INSENSITIVE);
			
			spaceResFlux = spaceResFlux.filter(spaceRes -> {
				Matcher m = filterPattern.matcher(spaceRes.getName());
				return m.matches();
			});
		}
		
		return spaceResFlux.map(spaceRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.setResolvedSpaceId(spaceRes.getId());
			itnew.setResolvedSpaceName(spaceRes.getName());
			
			return itnew;
		});
	}
	
	private Flux<IntermediateTarget> resolveApplication(IntermediateTarget it) {
		/* NB: Now we have to consider three cases:
		 * Case 1: both applicationName and applicationRegex is empty => select all applications (in the space)
		 * Case 2: applicationName is null, but applicationRegex is filled => filter all applications with the regex
		 * Case 3: applicationName is filled, but applicationRegex is null => select a single application
		 * In cases 1 and 2, we need the list of all applications in the space.
		 */
		
		if (it.getConfigTarget().getApplicationRegex() == null && it.getConfigTarget().getApplicationName() != null) {
			// Case 3: we have the applicationName, but we also need its id
			
			String appNameToSearchFor = it.getConfigTarget().getApplicationName().toLowerCase(Locale.ENGLISH);
			
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveAllApplicationsInSpaceV3(it.getResolvedOrgId(), it.getResolvedSpaceId())
					.map(ListApplicationsResponse::getResources)
					.flatMapMany(Flux::fromIterable)
					.filter(appResource -> appNameToSearchFor.equals(appResource.getName().toLowerCase(Locale.ENGLISH)))
					.single()
					.doOnError(e -> {
						if (e instanceof NoSuchElementException) {
							logEmptyTarget.warn("Application id could not be found for org '{}', space '{}' and application '{}'. Check your configuration of targets; skipping it for now; this message may be muted by setting the log level of the emitting logger accordingly!", it.getResolvedOrgName(), it.getResolvedSpaceName(), it.getConfigTarget().getApplicationName());
						}
					})
					.onErrorResume(e -> Mono.empty())
					.filter( res -> this.isApplicationInScrapableState(res.getState()))
					.map(res -> {
						it.setResolvedApplicationName(res.getName());
						it.setResolvedApplicationId(res.getId());
						return it;
					}).doOnError(e ->
						log.warn("Error on retrieving application id for org '{}', space '{}' and application '{}'", it.getResolvedOrgName(), it.getResolvedSpaceName(), it.getConfigTarget().getApplicationName(), e)
					)
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all applications in the current space
		Mono<ListApplicationsResponse> responseMono = this.cfAccessor.retrieveAllApplicationsInSpaceV3(it.getResolvedOrgId(), it.getResolvedSpaceId());

		Flux<ApplicationResource> appResFlux = responseMono.map(ListApplicationsResponse::getResources)
			.flatMapMany(Flux::fromIterable)
			.doOnError(e -> log.warn("Error on retrieving list of applications in org '{}' and space '{}'", it.getResolvedOrgName(), it.getResolvedSpaceName(), e))
			.onErrorResume(__ -> Flux.empty());
		
		if (it.getConfigTarget().getApplicationRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.getConfigTarget().getApplicationRegex(), Pattern.CASE_INSENSITIVE);
			
			appResFlux = appResFlux.filter(appRes -> {
				Matcher m = filterPattern.matcher(appRes.getName());
				return m.matches();
			});
		}
		
		Flux<ApplicationResource> scrapableFlux = appResFlux.filter(appRes ->
				this.isApplicationInScrapableState(appRes.getState()));
		
		return scrapableFlux.map(appRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.setResolvedApplicationId(appRes.getId());
			itnew.setResolvedApplicationName(appRes.getName());
			
			return itnew;
		});
	}

	private Flux<IntermediateTarget> resolveAnnotations(IntermediateTarget it) {
		if (Boolean.TRUE.equals(it.getConfigTarget().getKubernetesAnnotations())) {
			Mono<ListApplicationsResponse> response = this.cfAccessor
				.retrieveAllApplicationsInSpaceV3(it.getResolvedOrgId(), it.getResolvedSpaceId());

			return response.flatMap(res -> {
				if (res == null || INVALID_APPLICATIONS_RESPONSE == res) {
					logEmptyTarget.debug("Your foundation does not support V3 APIs, yet you have enabled Kubernetes Annotation filtering. Ignoring annotation filtering.");
					return Mono.just(it);
				}

				return res.getResources().stream()
						.filter(app -> it.getResolvedApplicationName().equals(app.getName().toLowerCase(Locale.ENGLISH)))
						.filter(app -> this.isApplicationInScrapableState(app.getState()))
						.filter(app -> app.getMetadata() != null)
						.filter(app -> app.getMetadata().getAnnotations() != null)
						.filter(app -> app.getMetadata().getAnnotations().getOrDefault(PROMETHEUS_IO_SCRAPE, "false").equals("true"))
						.map(app -> {
							it.setResolvedMetricsPath(app.getMetadata().getAnnotations().getOrDefault(PROMETHEUS_IO_PATH, null));
							return Mono.just(it);
						}).findFirst().orElseGet(Mono::empty);
			}).doOnError(e ->
				 log.warn("Error on retrieving application annotations for org '{}', space '{}' and application '{}'.",
						it.getResolvedOrgName(), it.getResolvedSpaceName(), it.getConfigTarget().getApplicationName(), e)).flux();
		}

		return Mono.just(it).flux();
	}

	private boolean isApplicationInScrapableState(ApplicationState applicationState) {
		if (applicationState == ApplicationState.STARTED) {
			return true;
		}
		
		/* TODO: To be enhanced, once we know of further states, which are
		 * also scrapeable.
		 */
		
		return false;
	}
}
