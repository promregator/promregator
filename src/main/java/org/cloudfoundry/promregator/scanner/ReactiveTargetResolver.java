package org.cloudfoundry.promregator.scanner;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveTargetResolver implements TargetResolver {
	private static final Logger log = Logger.getLogger(ReactiveTargetResolver.class);
	
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
		}
		
		public ResolvedTarget toResolvedTarget() {
			ResolvedTarget rt = new ResolvedTarget();
			rt.setOriginalTarget(this.configTarget);
			rt.setOrgName(this.resolvedOrgName);
			rt.setSpaceName(this.resolvedSpaceName);
			rt.setApplicationName(this.resolvedApplicationName);
			rt.setProtocol(this.configTarget.getProtocol());
			rt.setPath(this.configTarget.getPath());
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
		
		
	}
	
	@Override
	public List<ResolvedTarget> resolveTargets(List<Target> configTargets) {
		
		Flux<IntermediateTarget> initialFlux = Flux.fromIterable(configTargets)
				.map(configTarget -> {
					IntermediateTarget it = new IntermediateTarget();
					it.setConfigTarget(configTarget);
					
					return it;
				});
		
		Flux<IntermediateTarget> orgResolvedFlux = initialFlux.flatMap(it -> this.resolveOrg(it)).log(log.getName()+".resolveOrg");
		Flux<IntermediateTarget> spaceResolvedFlux = orgResolvedFlux.flatMap(it -> this.resolveSpace(it)).log(log.getName()+".resolveSpace");
		Flux<IntermediateTarget> applicationResolvedFlux = spaceResolvedFlux.flatMap(it -> this.resolveApplication(it)).log(log.getName()+".resolveApplication")
				.doOnNext(it -> {
					if (it.getResolvedOrgId() == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved org id", it.getConfigTarget()));
					}
					
					if (it.getResolvedSpaceId() == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved space id", it.getConfigTarget()));
					}
					
					if (it.getResolvedApplicationId() == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved application id", it.getConfigTarget()));
					}
					
					if (it.getResolvedOrgName() == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved org name", it.getConfigTarget()));
					}
					
					if (it.getResolvedSpaceName() == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved space name", it.getConfigTarget()));
					}
					
					if (it.getResolvedApplicationName() == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved application name", it.getConfigTarget()));
					}
				});
		
		Flux<ResolvedTarget> resultFlux = applicationResolvedFlux.map(it -> it.toResolvedTarget());
		
		List<ResolvedTarget> resultList = resultFlux.distinct().collectList().block();
		if (log.isDebugEnabled()) {
			log.debug(String.format("Successfully resolved %d configuration targets to %d resolved targets", configTargets.size(), resultList.size()));
		}
		
		return resultList;
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
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveOrgId(it.getConfigTarget().getOrgName())
					.map(lor -> lor.getResources())
					.flatMap(resList -> {
						if (resList == null || resList.isEmpty()) {
							return Mono.empty();
						}
						
						return Mono.just(resList.get(0));
					})
					.map(res -> {
						it.setResolvedOrgName(res.getEntity().getName());
						it.setResolvedOrgId(res.getMetadata().getId());
						return it;
					})
					.doOnError(e -> {
						log.warn(String.format("Error on retrieving org id for org '%s'", it.getConfigTarget().getOrgName()), e);
					})
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all orgs from the platform
		Mono<ListOrganizationsResponse> responseMono = this.cfAccessor.retrieveAllOrgIds();

		Flux<OrganizationResource> orgResFlux = responseMono.map(resp -> resp.getResources())
			.flatMapMany(list -> Flux.fromIterable(list));
		
		if (it.getConfigTarget().getOrgRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.getConfigTarget().getOrgRegex(), Pattern.CASE_INSENSITIVE);
			
			orgResFlux = orgResFlux.filter(orgRes -> {
				Matcher m = filterPattern.matcher(orgRes.getEntity().getName());
				return m.matches();
			});
		}
		
		Flux<IntermediateTarget> result = orgResFlux.map(orgRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.setResolvedOrgId(orgRes.getMetadata().getId());
			itnew.setResolvedOrgName(orgRes.getEntity().getName());
			
			return itnew;
		});
		
		return result;
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
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveSpaceId(it.getResolvedOrgId(), it.getConfigTarget().getSpaceName())
					.map(lsr -> lsr.getResources())
					.flatMap(resList -> {
						if (resList == null || resList.isEmpty()) {
							return Mono.empty();
						}
						
						return Mono.just(resList.get(0));
					})
					.map(res -> {
						it.setResolvedSpaceName(res.getEntity().getName());
						it.setResolvedSpaceId(res.getMetadata().getId());
						return it;
					}).doOnError(e -> {
						log.warn(String.format("Error on retrieving space id for org '%s' and space '%s'", it.getResolvedOrgName(), it.getConfigTarget().getSpaceName()), e);
					})
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all spaces in the current org
		Mono<ListSpacesResponse> responseMono = this.cfAccessor.retrieveSpaceIdsInOrg(it.getResolvedOrgId());

		Flux<SpaceResource> spaceResFlux = responseMono.map(resp -> resp.getResources())
			.flatMapMany(list -> Flux.fromIterable(list));
		
		if (it.getConfigTarget().getSpaceRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.getConfigTarget().getSpaceRegex(), Pattern.CASE_INSENSITIVE);
			
			spaceResFlux = spaceResFlux.filter(spaceRes -> {
				Matcher m = filterPattern.matcher(spaceRes.getEntity().getName());
				return m.matches();
			});
		}
		
		Flux<IntermediateTarget> result = spaceResFlux.map(spaceRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.setResolvedSpaceId(spaceRes.getMetadata().getId());
			itnew.setResolvedSpaceName(spaceRes.getEntity().getName());
			
			return itnew;
		});
		
		return result;
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
			
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveAllApplicationIdsInSpace(it.getResolvedOrgId(), it.getResolvedSpaceId())
					.map(lsr -> lsr.getResources())
					.flatMapMany(list -> Flux.fromIterable(list))
					.filter(appResource -> {
						return appNameToSearchFor.equals(appResource.getEntity().getName().toLowerCase(Locale.ENGLISH));
					})
					.single()
					.doOnError(e -> {
						if (e instanceof NoSuchElementException) {
							log.warn(String.format("Application id could not be found for org '%s', space '%s' and application '%s'. Check your configuration; skipping it for now", it.getResolvedOrgName(), it.getResolvedSpaceName(), it.getConfigTarget().getApplicationName()));
						}
					})
					.onErrorResume(e -> {
						return Mono.empty();
					})
					.filter( res -> {
						return this.isApplicationInScrapableState(res.getEntity().getState());
					})
					.map(res -> {
						it.setResolvedApplicationName(res.getEntity().getName());
						it.setResolvedApplicationId(res.getMetadata().getId());
						return it;
					}).doOnError(e -> {
						log.warn(String.format("Error on retrieving application id for org '%s', space '%s' and application '%s'", it.getResolvedOrgName(), it.getResolvedSpaceName(), it.getConfigTarget().getApplicationName()), e);
					})
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all applications in the current space
		Mono<ListApplicationsResponse> responseMono = this.cfAccessor.retrieveAllApplicationIdsInSpace(it.getResolvedOrgId(), it.getResolvedSpaceId());

		Flux<ApplicationResource> appResFlux = responseMono.map(resp -> resp.getResources())
			.flatMapMany(list -> Flux.fromIterable(list))
			.doOnError(e -> {
				log.warn(String.format("Error on retrieving list of applications in org '%s' and space '%s'", it.getResolvedOrgName(), it.getResolvedSpaceName()), e);
			})
			.onErrorResume(__ -> Flux.empty());
		
		if (it.getConfigTarget().getApplicationRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.getConfigTarget().getApplicationRegex(), Pattern.CASE_INSENSITIVE);
			
			appResFlux = appResFlux.filter(appRes -> {
				Matcher m = filterPattern.matcher(appRes.getEntity().getName());
				return m.matches();
			});
		}
		
		Flux<ApplicationResource> scrapableFlux = appResFlux.filter(appRes -> {
			return this.isApplicationInScrapableState(appRes.getEntity().getState());
		});
		
		Flux<IntermediateTarget> result = scrapableFlux.map(appRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.setResolvedApplicationId(appRes.getMetadata().getId());
			itnew.setResolvedApplicationName(appRes.getEntity().getName());
			
			return itnew;
		});
		
		return result;
	}
	
	private boolean isApplicationInScrapableState(String state) {
		if ("STARTED".equals(state)) {
			return true;
		}
		
		/* TODO: To be enhanced, once we know of further states, which are
		 * also scrapeable.
		 */
		
		return false;
	}
}
