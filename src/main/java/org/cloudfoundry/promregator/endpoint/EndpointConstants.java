package org.cloudfoundry.promregator.endpoint;

public interface EndpointConstants {
	static final String ENDPOINT_PATH_DISCOVERY = "/discovery";
	static final String ENDPOINT_PATH_SINGLE_ENDPOINT_SCRAPING = "/metrics";
	static final String ENDPOINT_PATH_SINGLE_TARGET_SCRAPING = "/singleTargetMetrics";
	static final String ENDPOINT_PATH_PROMREGATOR_METRICS = "/promregatorMetrics";
	static final String ENDPOINT_PATH_CACHE_INVALIDATION = "/cache/invalidate";
	
	static final String HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER = "X-Promregator-Instance";
}
