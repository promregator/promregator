package org.cloudfoundry.promregator.auth;

import org.apache.http.client.methods.HttpGet;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Consumer;

public interface AuthenticationEnricher {
	/**
	 *
	 * @deprecated and HttpClient support will be removed in favor of WebClient flavor
	 */
	@Deprecated
	void enrichWithAuthentication(HttpGet httpget);

	Consumer<WebClient.Builder> lookupEnrichAuthentication();
}
