package org.cloudfoundry.promregator.cfaccessor.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.v3.AbstractClientV3Operations;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Call the v3 info API and returns a JsonNode. Since we do not care about the actual response (other than errors), we do not need to
 * model the whole response object.
 * Issue: https://github.com/cloudfoundry/cf-java-client/issues/1097
 */
public class ReactorInfoV3 extends AbstractClientV3Operations {

	public ReactorInfoV3(ConnectionContext connectionContext, Mono<String> root, TokenProvider tokenProvider, Map<String, String> requestTags) {
		super(connectionContext, root, tokenProvider, requestTags);
	}

	public Mono<JsonNode> get() {
		return get("", JsonNode.class, builder -> builder.pathSegment("info"))
			.checkpoint();
	}
}
