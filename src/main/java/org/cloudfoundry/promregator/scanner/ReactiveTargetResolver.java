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

import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveTargetResolver implements TargetResolver {
	private static final Logger log = Logger.getLogger(ReactiveTargetResolver.class);
	private static final Logger logEmptyTarget = Logger.getLogger(String.format("%s.EmptyTarget", ReactiveTargetResolver.class.getName()));

	@Autowired
	private CFAccessor cfAccessor;

	@Value("${cf.request.retires:3}")
	private int maxRetries;

	@Value("${cf.request.max:500}")
	private int maxRequests;


	private static class IntermediateTarget {
		public Target configTarget;
		public String resolvedOrgName;
		public String resolvedOrgId;
		public String resolvedSpaceName;
		public String resolvedSpaceId;
		public String resolvedApplicationName;
		public String resolvedApplicationId;
		
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
	}
	
	@Override
	public List<ResolvedTarget> resolveTargets(List<Target> configTargets) {
		
		Flux<IntermediateTarget> initialFlux = Flux.fromIterable(configTargets)
				.map(configTarget -> {
					IntermediateTarget it = new IntermediateTarget();
					it.configTarget = configTarget;
					
					return it;
				});
		
		Flux<IntermediateTarget> orgResolvedFlux = initialFlux.flatMap(it -> this.resolveOrg(it)).log(log.getName()+".resolveOrg");
		Flux<IntermediateTarget> spaceResolvedFlux = orgResolvedFlux.flatMap(it -> this.resolveSpace(it)).log(log.getName()+".resolveSpace");
		Flux<IntermediateTarget> applicationResolvedFlux = spaceResolvedFlux.flatMap(it -> this.resolveApplication(it)).log(log.getName()+".resolveApplication")
				.doOnNext(it -> {
					if (it.resolvedOrgId == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved org id", it.configTarget));
					}
					
					if (it.resolvedSpaceId == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved space id", it.configTarget));
					}
					
					if (it.resolvedApplicationId == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved application id", it.configTarget));
					}
					
					if (it.resolvedOrgName == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved org name", it.configTarget));
					}
					
					if (it.resolvedSpaceName == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved space name", it.configTarget));
					}
					
					if (it.resolvedApplicationName == null) {
						log.error(String.format("Target '%s' created a ResolvedTarget without resolved application name", it.configTarget));
					}
				});
		
		Flux<ResolvedTarget> resultFlux = applicationResolvedFlux.map(it -> it.toResolvedTarget());

		List<ResolvedTarget> resultList = resultFlux.distinct().collectList().compose(new BackPressureOps<>(maxRetries, maxRequests)).block();
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
		
		if (it.configTarget.getOrgRegex() == null && it.configTarget.getOrgName() != null) {
			// Case 3: we have the orgName, but we also need its id
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveOrgId(it.configTarget.getOrgName())
					.map(lor -> lor.getResources())
					.flatMap(resList -> {
						if (resList == null || resList.isEmpty()) {
							return Mono.empty();
						}
						
						return Mono.just(resList.get(0));
					})
					.map(res -> {
						it.resolvedOrgName = res.getEntity().getName();
						it.resolvedOrgId = res.getMetadata().getId();
						return it;
					})
					.doOnError(e -> {
						log.warn(String.format("Error on retrieving org id for org '%s'", it.configTarget.getOrgName()), e);
					})
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all orgs from the platform
		Mono<ListOrganizationsResponse> responseMono = this.cfAccessor.retrieveAllOrgIds();

		Flux<OrganizationResource> orgResFlux = responseMono.map(resp -> resp.getResources())
			.flatMapMany(list -> Flux.fromIterable(list));
		
		if (it.configTarget.getOrgRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.configTarget.getOrgRegex(), Pattern.CASE_INSENSITIVE);
			
			orgResFlux = orgResFlux.filter(orgRes -> {
				Matcher m = filterPattern.matcher(orgRes.getEntity().getName());
				return m.matches();
			});
		}
		
		Flux<IntermediateTarget> result = orgResFlux.map(orgRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.resolvedOrgId = orgRes.getMetadata().getId();
			itnew.resolvedOrgName = orgRes.getEntity().getName();
			
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
		
		if (it.configTarget.getSpaceRegex() == null && it.configTarget.getSpaceName() != null) {
			// Case 3: we have the spaceName, but we also need its id
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveSpaceId(it.resolvedOrgId, it.configTarget.getSpaceName())
					.map(lsr -> lsr.getResources())
					.flatMap(resList -> {
						if (resList == null || resList.isEmpty()) {
							return Mono.empty();
						}
						
						return Mono.just(resList.get(0));
					})
					.map(res -> {
						it.resolvedSpaceName = res.getEntity().getName();
						it.resolvedSpaceId = res.getMetadata().getId();
						return it;
					}).doOnError(e -> {
						log.warn(String.format("Error on retrieving space id for org '%s' and space '%s'", it.resolvedOrgName, it.configTarget.getSpaceName()), e);
					})
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all spaces in the current org
		Mono<ListSpacesResponse> responseMono = this.cfAccessor.retrieveSpaceIdsInOrg(it.resolvedOrgId);

		Flux<SpaceResource> spaceResFlux = responseMono.map(resp -> resp.getResources())
			.flatMapMany(list -> Flux.fromIterable(list));
		
		if (it.configTarget.getSpaceRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.configTarget.getSpaceRegex(), Pattern.CASE_INSENSITIVE);
			
			spaceResFlux = spaceResFlux.filter(spaceRes -> {
				Matcher m = filterPattern.matcher(spaceRes.getEntity().getName());
				return m.matches();
			});
		}
		
		Flux<IntermediateTarget> result = spaceResFlux.map(spaceRes -> {
			IntermediateTarget itnew = new IntermediateTarget(it);
			itnew.resolvedSpaceId = spaceRes.getMetadata().getId();
			itnew.resolvedSpaceName = spaceRes.getEntity().getName();
			
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
		
		if (it.configTarget.getApplicationRegex() == null && it.configTarget.getApplicationName() != null) {
			// Case 3: we have the applicationName, but we also need its id
			
			String appNameToSearchFor = it.configTarget.getApplicationName().toLowerCase(Locale.ENGLISH);
			
			Mono<IntermediateTarget> itMono = this.cfAccessor.retrieveAllApplicationIdsInSpace(it.resolvedOrgId, it.resolvedSpaceId)
					.map(lsr -> lsr.getResources())
					.flatMapMany(list -> Flux.fromIterable(list))
					.filter(appResource -> {
						return appNameToSearchFor.equals(appResource.getEntity().getName().toLowerCase(Locale.ENGLISH));
					})
					.single()
					.doOnError(e -> {
						if (e instanceof NoSuchElementException) {
							logEmptyTarget.warn(String.format("Application id could not be found for org '%s', space '%s' and application '%s'. Check your configuration of targets; skipping it for now; this message may be muted by setting the log level of the emitting logger accordingly!", it.resolvedOrgName, it.resolvedSpaceName, it.configTarget.getApplicationName()));
						}
					})
					.onErrorResume(e -> {
						return Mono.empty();
					})
					.filter( res -> {
						return this.isApplicationInScrapableState(res.getEntity().getState());
					})
					.map(res -> {
						it.resolvedApplicationName = res.getEntity().getName();
						it.resolvedApplicationId = res.getMetadata().getId();
						return it;
					}).doOnError(e -> {
						log.warn(String.format("Error on retrieving application id for org '%s', space '%s' and application '%s'", it.resolvedOrgName, it.resolvedSpaceName, it.configTarget.getApplicationName()), e);
					})
					.onErrorResume(__ -> Mono.empty());
			
			return itMono.flux();
		}
		
		// Case 1 & 2: Get all applications in the current space
		Mono<ListApplicationsResponse> responseMono = this.cfAccessor.retrieveAllApplicationIdsInSpace(it.resolvedOrgId, it.resolvedSpaceId);

		Flux<ApplicationResource> appResFlux = responseMono.map(resp -> resp.getResources())
			.flatMapMany(list -> Flux.fromIterable(list))
			.doOnError(e -> {
				log.warn(String.format("Error on retrieving list of applications in org '%s' and space '%s'", it.resolvedOrgName, it.resolvedSpaceName), e);
			})
			.onErrorResume(__ -> Flux.empty());
		
		if (it.configTarget.getApplicationRegex() != null) {
			// Case 2
			final Pattern filterPattern = Pattern.compile(it.configTarget.getApplicationRegex(), Pattern.CASE_INSENSITIVE);
			
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
			itnew.resolvedApplicationId = appRes.getMetadata().getId();
			itnew.resolvedApplicationName = appRes.getEntity().getName();
			
			return itnew;
		});
		
		return result;
	}
	
	private boolean isApplicationInScrapableState(String state) {
		if ("STARTED".equals(state)) {
			return true;
		}
		
		/* TODO: To be enhanced, once we know of further states, which are
		 * also scrapable.
		 */
		
		return false;
	}
}
