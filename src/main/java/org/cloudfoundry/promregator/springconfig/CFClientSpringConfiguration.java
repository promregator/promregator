package org.cloudfoundry.promregator.springconfig;

import java.util.Optional;
import java.util.regex.Pattern;

import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext.Builder;
import org.cloudfoundry.reactor.ProxyConfiguration;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CFClientSpringConfiguration {
	@Bean
	public DefaultConnectionContext connectionContext(
			@Value("${cf.api_host}") String apiHost, 
			@Value("${cf.skipSslValidation:false}") boolean skipSSLValidation, 
			Optional<ProxyConfiguration> proxyConfiguration) throws ConfigurationException {
		if (apiHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(apiHost).find()) {
			throw new ConfigurationException("cf.api_host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}

		Builder connctx = DefaultConnectionContext.builder().apiHost(apiHost).skipSslValidation(skipSSLValidation);
		
		if (proxyConfiguration.isPresent()) {
			connctx = connctx.proxyConfiguration(proxyConfiguration);
		}
		return connctx.build();
	}

	@Bean
	public PasswordGrantTokenProvider tokenProvider(@Value("${cf.username}") String username, @Value("${cf.password}") String password) {
		return PasswordGrantTokenProvider.builder().password(password).username(username).build();
	}

	private static final Pattern PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://");
	
	@Bean
	public ProxyConfiguration proxyConfiguration(@Value("${cf.proxyHost:#{null}}") String proxyHost, @Value("${cf.proxyPort:0}") int proxyPort) throws ConfigurationException {
		if (proxyHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(proxyHost).find()) {
			throw new ConfigurationException("cf.proxyHost configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}
		
		if (proxyHost != null && proxyPort != 0) {
			return ProxyConfiguration.builder().host(proxyHost).port(proxyPort).build();
		} else {
			return null;
		}
	}
	
	@Bean
	public ReactorCloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorCloudFoundryClient.builder().connectionContext(connectionContext).tokenProvider(tokenProvider).build();
	}

}
