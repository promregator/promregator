package org.cloudfoundry.promregator.endpoint;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorCacheClassic;
import org.cloudfoundry.promregator.endpoint.MockedAppInstanceScannerEndpointSpringApplication.MockedCFAccessorCache;
import org.cloudfoundry.promregator.endpoint.MockedAppInstanceScannerEndpointSpringApplication.MockedCachingTargetResolver;
import org.cloudfoundry.promregator.scanner.TargetResolver;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MockedAppInstanceScannerEndpointSpringApplication.class)
@TestPropertySource(locations="default.properties")
public class InvalidateCacheEndpointTest {

	@AfterClass
	public static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	
	@Autowired
	private InvalidateCacheEndpoint subject;
	
	@Autowired
	private CFAccessorCacheClassic cfAccessorCache;
	
	@Autowired
	private TargetResolver targetResolver;
	
	@Test
	public void testInvalidateCacheAll() {
		Assert.assertNotNull(subject);
		
		ResponseEntity<String> response = subject.invalidateCache(true, true, true, true);
		
		Assert.assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
		
		MockedCFAccessorCache cfa = (MockedCFAccessorCache) this.cfAccessorCache;
		Assert.assertTrue(cfa.isApplicationCache());
		Assert.assertTrue(cfa.isOrgCache());
		Assert.assertTrue(cfa.isSpaceCache());
		
		MockedCachingTargetResolver tr = (MockedCachingTargetResolver) this.targetResolver;
		Assert.assertTrue(tr.isResolverCache());
	}

}
