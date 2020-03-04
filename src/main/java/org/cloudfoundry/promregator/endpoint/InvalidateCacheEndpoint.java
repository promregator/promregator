package org.cloudfoundry.promregator.endpoint;

import org.cloudfoundry.promregator.cfaccessor.CFAccessorCache;
import org.cloudfoundry.promregator.scanner.CachingTargetResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(EndpointConstants.ENDPOINT_PATH_CACHE_INVALIDATION)
public class InvalidateCacheEndpoint {

	private final CFAccessorCache cfAccessorCache;
	private final CachingTargetResolver cachingTargetResolver;

	public InvalidateCacheEndpoint(CFAccessorCache cfAccessorCache, CachingTargetResolver cachingTargetResolver) {
		this.cfAccessorCache = cfAccessorCache;
		this.cachingTargetResolver = cachingTargetResolver;
	}

	@GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> invalidateCache(
			@RequestParam(name = "application", required = false) boolean application,
			@RequestParam(name = "space", required = false) boolean space,
			@RequestParam(name = "org", required = false) boolean org,
			@RequestParam(name = "resolver", required = false) boolean resolver
			) {

		if (application) {
			cfAccessorCache.invalidateCacheApplications();
		}
		
		if (space) {
			cfAccessorCache.invalidateCacheSpace();
		}
		
		if (org) {
			cfAccessorCache.invalidateCacheOrg();
		}
		
		if (resolver) {
			this.cachingTargetResolver.invalidateCache();
		}

		return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
	}
}
