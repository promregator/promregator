package org.cloudfoundry.promregator.endpoint;

import org.cloudfoundry.promregator.scanner.AppInstanceScanner;
import org.junit.Assert;
import org.junit.Before;
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
@TestPropertySource(locations="default.properties")
public class InvalidateCacheEndpointTest {

	@Autowired
	private InvalidateCacheEndpoint subject;
	
	@Autowired
	private AppInstanceScanner appInstanceScanner;
	
	@Before
	public void resetAIS() {
		MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner ais = (MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner) this.appInstanceScanner;
		ais.reset();
	}
	
	@Test
	public void testInvalidateCacheAll() {
		Assert.assertNotNull(subject);
		
		ResponseEntity<String> response = subject.invalidateCache(true, true, true);
		
		Assert.assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
		
		MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner ais = (MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner) this.appInstanceScanner;
		
		Assert.assertTrue(ais.isAppInvalidated());
		Assert.assertTrue(ais.isSpaceInvalidated());
		Assert.assertTrue(ais.isOrgInvalidated());
	}
	
	@Test
	public void testInvalidateCacheNone() {
		Assert.assertNotNull(subject);
		
		ResponseEntity<String> response = subject.invalidateCache(false, false, false);
		
		Assert.assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
		
		MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner ais = (MockedAppInstanceScannerEndpointSpringApplication.MockedReactiveAppInstanceScanner) this.appInstanceScanner;
		
		Assert.assertFalse(ais.isAppInvalidated());
		Assert.assertFalse(ais.isSpaceInvalidated());
		Assert.assertFalse(ais.isOrgInvalidated());
	}

}
