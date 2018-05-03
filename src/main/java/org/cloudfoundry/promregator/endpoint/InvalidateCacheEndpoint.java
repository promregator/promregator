package org.cloudfoundry.promregator.endpoint;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.ReactiveCFAccessorImpl;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.ReactiveAppInstanceScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cache/invalidate")
public class InvalidateCacheEndpoint {

	@Autowired
	private CFAccessor cfAccessor;
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> invalidateCache(
			@RequestParam(name = "application", required = false) boolean application,
			@RequestParam(name = "space", required = false) boolean space,
			@RequestParam(name = "org", required = false) boolean org
			) {

		ReactiveCFAccessorImpl reactiveCFAccessor = (ReactiveCFAccessorImpl) this.cfAccessor;
		
		ReactiveAppInstanceScanner reactiveAppInstanceScanner = (ReactiveAppInstanceScanner) this.appInstanceScanner;
		
		if (application) {
			reactiveAppInstanceScanner.invalidateApplicationUrlCache();
			reactiveCFAccessor.invalidateCacheApplications();
		}
		
		if (space) {
			reactiveCFAccessor.invalidateCacheSpace();
		}
		
		if (org) {
			reactiveCFAccessor.invalidateCacheOrg();
		}

		return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
	}
}
