package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Consumer;

/**
 * The NullEnricher is an AuthenticationEnricher, which does not enrich 
 * the HTTP request. It can be used in case an AuthenticationEnricher is required,
 * but no operation shall be performed
 *
 */
public class NullEnricher implements AuthenticationEnricher {

	@Override
	public void enrichWithAuthentication(HttpGet httpget) {
		// left blank intentionally
	}

	@Override
	public Consumer<WebClient.Builder> lookupEnrichAuthentication() {
		return builder -> {
			//No action taken, this is a noop enricher
		};
	}

}
