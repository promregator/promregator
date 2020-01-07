package org.cloudfoundry.promregator.endpoint;

public interface EndpointConstants {
	String ENDPOINT_PATH_DISCOVERY = "/discovery";
	String ENDPOINT_PATH_SINGLE_ENDPOINT_SCRAPING = "/metrics";
	String ENDPOINT_PATH_SINGLE_TARGET_SCRAPING = "/singleTargetMetrics";
	String ENDPOINT_PATH_PROMREGATOR_METRICS = "/promregatorMetrics";
	String ENDPOINT_PATH_CACHE_INVALIDATION = "/cache/invalidate";
	
	String HTTP_HEADER_PROMREGATOR_INSTANCE_IDENTIFIER = "X-Promregator-Instance";
}
