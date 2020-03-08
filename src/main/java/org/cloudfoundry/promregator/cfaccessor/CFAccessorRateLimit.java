package org.cloudfoundry.promregator.cfaccessor;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;

import com.google.common.util.concurrent.RateLimiter;

import io.prometheus.client.Histogram.Timer;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CFAccessorRateLimit implements CFAccessor {
	private static final Logger log = Logger.getLogger(CFAccessorRateLimit.class);
	
	private final CFAccessor parent;
	private final RateLimiter cfccRateLimiter;

	private final InternalMetrics internalMetrics;
	
	public CFAccessorRateLimit(CFAccessor parent, double requestRateLimit, InternalMetrics internalMetrics) {
		this.parent = parent;
		this.cfccRateLimiter = RateLimiter.create(requestRateLimit);
		this.internalMetrics = internalMetrics;
	}
	
	private Mono<Object> rateLimitingMono(RequestType requestType) {
		return Mono.fromCallable(() -> {
			
			Timer startTimerRateLimit = null;
			if (this.internalMetrics != null) {
				startTimerRateLimit = this.internalMetrics.startTimerRateLimit(requestType.getMetricName());
			}
			
			double waitTime = cfccRateLimiter.acquire(1);
			
			if (startTimerRateLimit != null) {
				startTimerRateLimit.observeDuration();
			}
			
			if (waitTime > 0.001) {
				log.debug(String.format("Rate Limiting has throttled request for %.3f seconds", waitTime));
			}
			
			return new Object();
		}).subscribeOn(Schedulers.elastic())
		.flatMap(x -> Mono.empty());
	}
	
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		return this.rateLimitingMono(RequestType.ORG).then(parent.retrieveOrgId(orgName));
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		return this.rateLimitingMono(RequestType.ALL_ORGS).then(parent.retrieveAllOrgIds());
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		return this.rateLimitingMono(RequestType.SPACE).then(parent.retrieveSpaceId(orgId, spaceName));
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		return this.rateLimitingMono(RequestType.SPACE_IN_ORG).then(parent.retrieveSpaceIdsInOrg(orgId));
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		return this.rateLimitingMono(RequestType.ALL_APPS_IN_SPACE).then(parent.retrieveAllApplicationIdsInSpace(orgId, spaceId));
	}

	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		return this.rateLimitingMono(RequestType.SPACE_SUMMARY).then(parent.retrieveSpaceSummary(spaceId));
	}

	public CFAccessor getParent() {
		return parent;
	}

}
