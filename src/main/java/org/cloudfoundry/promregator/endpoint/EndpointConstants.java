package org.cloudfoundry.promregator.endpoint;

public interface EndpointConstants {
	final static String ENDPOINT_PATH_DISCOVERY = "/discovery";
	final static String ENDPOINT_PATH_SINGLE_ENDPOINT_SCRAPING = "/metrics";
	final static String ENDPOINT_PATH_SINGLE_TARGET_SCRAPING = "/singleTargetMetrics";
	final static String ENDPOINT_PATH_PROMREGATOR_METRICS = "/promregatorMetrics";
	final static String ENDPOINT_PATH_CACHE_INVALIDATION = "/cache/invalidate";
}
