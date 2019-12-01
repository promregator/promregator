package org.cloudfoundry.promregator;

import io.prometheus.client.CollectorRegistry;

public class JUnitTestUtils {

	private JUnitTestUtils() {}

	public static void cleanUpAll() {
		cleanupRegistry();
	}

	private static void cleanupRegistry() {
		CollectorRegistry.defaultRegistry.clear();
	}
}
