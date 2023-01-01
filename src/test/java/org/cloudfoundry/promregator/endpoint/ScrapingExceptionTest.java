package org.cloudfoundry.promregator.endpoint;

import org.junit.jupiter.api.Test;

public class ScrapingExceptionTest {

	@Test
	public void testScrapingExceptionStringThrowable() {
		new ScrapingException("Test", new Exception());
	}

	@Test
	public void testScrapingExceptionString() {
		new ScrapingException("Test");
	}

}
