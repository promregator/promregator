package org.cloudfoundry.promregator.cfaccessor;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;

import com.google.common.util.concurrent.RateLimiter;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CFAccessorRateLimit implements CFAccessor {
	private static final Logger log = Logger.getLogger(CFAccessorRateLimit.class);
	
	private final CFAccessor parent;
	private final RateLimiter cfccRateLimiter;
	
	public CFAccessorRateLimit(CFAccessor parent, double requestRateLimit) {
		this.parent = parent;
		this.cfccRateLimiter = RateLimiter.create(requestRateLimit);
	}
	
	private Mono<Object> rateLimitingMono() {
		return Mono.fromCallable(() -> {
			// todo: add an internal Metric here!
			double waitTime = cfccRateLimiter.acquire(1);
			
			if (waitTime > 0.001) {
				log.debug(String.format("Rate Limiting has throttled request for %.3f seconds", waitTime));
			}
			
			return new Object();
		}).subscribeOn(Schedulers.elastic())
		.flatMap(x -> Mono.empty());
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		return this.rateLimitingMono().then(parent.retrieveOrgId(orgName));
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		return this.rateLimitingMono().then(parent.retrieveAllOrgIds());
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		return this.rateLimitingMono().then(parent.retrieveSpaceId(orgId, spaceName));
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		return this.rateLimitingMono().then(parent.retrieveSpaceIdsInOrg(orgId));
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		return this.rateLimitingMono().then(parent.retrieveAllApplicationIdsInSpace(orgId, spaceId));
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		return this.rateLimitingMono().then(parent.retrieveSpaceSummary(spaceId));
	}

	public CFAccessor getParent() {
		return parent;
	}

}
