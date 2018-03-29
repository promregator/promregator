package org.cloudfoundry.promregator.springconfig;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.http.conn.util.InetAddressUtils;
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
			
			String proxyIP = null;
			if (!InetAddressUtils.isIPv4Address(proxyHost) && !InetAddressUtils.isIPv6Address(proxyHost)) {
				/*
				 * NB: There is currently a bug in io.netty.util.internal.SocketUtils.connect()
				 * which is called implicitly by the CF API Client library, which leads to the effect
				 * that a hostname for the proxy isn't resolved. Thus, it is only possible to pass 
				 * IP addresses as proxy names.
				 * To work around this issue, we manually perform a resolution of the hostname here
				 * and then feed that one to the CF API Client library...
				 */
				try {
					InetAddress ia = InetAddress.getByName(proxyHost);
					proxyIP = ia.getHostAddress();
				} catch (UnknownHostException e) {
					throw new ConfigurationException("The cf.proxyHost provided cannot be resolved to an IP address; is there a typo in your configuration?", e);
				}
			} else {
				// the address specified is already an IP address
				proxyIP = proxyHost;
			}
			
			return ProxyConfiguration.builder().host(proxyIP).port(proxyPort).build();
			
		} else {
			return null;
		}
	}
	
	@Bean
	public ReactorCloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorCloudFoundryClient.builder().connectionContext(connectionContext).tokenProvider(tokenProvider).build();
	}

}
