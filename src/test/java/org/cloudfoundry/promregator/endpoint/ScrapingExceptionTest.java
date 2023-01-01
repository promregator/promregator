package org.cloudfoundry.promregator.endpoint;

import org.junit.jupiter.api.Test;

class ScrapingExceptionTest {

	@Test
	void testScrapingExceptionStringThrowable() {
		new ScrapingException("Test", new Exception());
	}

	@Test
	void testScrapingExceptionString() {
		new ScrapingException("Test");
	}

}
