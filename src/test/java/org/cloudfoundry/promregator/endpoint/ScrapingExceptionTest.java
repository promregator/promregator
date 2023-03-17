package org.cloudfoundry.promregator.endpoint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScrapingExceptionTest {

	@Test
	public void testScrapingExceptionStringThrowable() {
		Assertions.assertNotNull(new ScrapingException("Test", new Exception()));
	}

	@Test
	public void testScrapingExceptionString() {
		Assertions.assertNotNull(new ScrapingException("Test"));
	}

}
