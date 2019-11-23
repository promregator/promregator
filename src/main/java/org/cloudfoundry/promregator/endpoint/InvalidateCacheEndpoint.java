package org.cloudfoundry.promregator.endpoint;

import org.cloudfoundry.promregator.cfaccessor.CFAccessorCache;
import org.cloudfoundry.promregator.scanner.CachingTargetResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(EndpointConstants.ENDPOINT_PATH_CACHE_INVALIDATION)
public class InvalidateCacheEndpoint {

	@Autowired
	private CFAccessorCache cfAccessorCache;
	
	@Autowired
	private CachingTargetResolver cachingTargetResolver;
	
	@RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
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
