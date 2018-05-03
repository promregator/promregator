package org.cloudfoundry.promregator.endpoint;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.endpoint.MockedAppInstanceScannerEndpointSpringApplication.MockedCachingTargetResolver;
import org.cloudfoundry.promregator.endpoint.MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveCFAccessorImpl;
import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = MockedAppInstanceScannerEndpointSpringApplication.class)
@TestPropertySource(locations="password_only.properties")
public class InvalidateCacheEndpointTest {

	@Autowired
	private InvalidateCacheEndpoint subject;
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@Autowired
	private CFAccessor cfAccessor;
	
	@Autowired
	private TargetResolver targetResolver;
	
	@Test
	public void testInvalidateCacheAll() {
		Assert.assertNotNull(subject);
		
		ResponseEntity<String> response = subject.invalidateCache(true, true, true, true);
		
		Assert.assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
		
		MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner ais = (MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner) this.appInstanceScanner;
		Assert.assertTrue(ais.isAppURLInvalidated());
		
		MockedReactiveCFAccessorImpl cfa = (MockedReactiveCFAccessorImpl) this.cfAccessor;
		Assert.assertTrue(cfa.isApplicationCache());
		Assert.assertTrue(cfa.isOrgCache());
		Assert.assertTrue(cfa.isSpaceCache());
		
		MockedCachingTargetResolver tr = (MockedCachingTargetResolver) this.targetResolver;
		Assert.assertTrue(tr.isResolverCache());
	}

}
