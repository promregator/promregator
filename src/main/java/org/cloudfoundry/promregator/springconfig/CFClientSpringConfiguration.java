package org.cloudfoundry.promregator.springconfig;

import java.util.Optional;

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
	public DefaultConnectionContext connectionContext(@Value("${cf.api_host}") String apiHost, Optional<ProxyConfiguration> proxyConfiguration) {
		Builder connctx = DefaultConnectionContext.builder().apiHost(apiHost);
		
		if (proxyConfiguration.isPresent()) {
			connctx = connctx.proxyConfiguration(proxyConfiguration);
		}
		return connctx.build();
	}

	@Bean
	public PasswordGrantTokenProvider tokenProvider(@Value("${cf.username}") String username, @Value("${cf.password}") String password) {
		return PasswordGrantTokenProvider.builder().password(password).username(username).build();
	}

	@Bean
	public ProxyConfiguration proxyConfiguration(@Value("${cf.proxyHost:@null}") String proxyHost, @Value("${cf.proxyPort:0}") int proxyPort) {
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
