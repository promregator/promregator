package org.cloudfoundry.promregator.websecurity;

import java.util.UUID;

import org.cloudfoundry.promregator.config.InboundAuthorizationMode;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
	private final PromregatorConfiguration promregatorConfiguration;
	public SecurityConfig(PromregatorConfiguration promregatorConfiguration) {
		this.promregatorConfiguration = promregatorConfiguration;
	}

	@Bean(name = BeanIds.AUTHENTICATION_MANAGER)
	@Override
	// see also
	// https://stackoverflow.com/questions/21633555/how-to-inject-authenticationmanager-using-java-configuration-in-a-custom-filter
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}

	private boolean isInboundAuthSecurityEnabled() {
		if (this.promregatorConfiguration.getDiscovery().getAuth() != InboundAuthorizationMode.NONE)
			return true;

		if (this.promregatorConfiguration.getEndpoint().getAuth() != InboundAuthorizationMode.NONE)
			return true;

		if (this.promregatorConfiguration.getMetrics().getAuth() != InboundAuthorizationMode.NONE)
			return true;

		if (this.promregatorConfiguration.getCache().getInvalidate().getAuth() != InboundAuthorizationMode.NONE)
			return true;

		return false;
	}

	@Value("${promregator.authentication.basic.username:promregator}")
	private String basicAuthUsername;

	@Value("${promregator.authentication.basic.password:#{null}}")
	private String basicAuthPassword;

	// see also
	// https://www.boraji.com/spring-security-4-http-basic-authentication-example
	// and
	// https://stackoverflow.com/questions/46999940/spring-boot-passwordencoder-error
	@Bean
	@Override
	public UserDetailsService userDetailsService() {
		InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

		String password = this.basicAuthPassword;
		if (password == null) {
			password = UUID.randomUUID().toString();

			// NB: Logging does not work here properly yet; better use stderr
			System.err.println();
			System.err.println(
					String.format("Using generated password for user %s: %s", this.basicAuthUsername, password));
			System.err.println();
		}

		manager.createUser(User.withUsername(this.basicAuthUsername).password(String.format("{noop}%s", password))
				.roles("USER").build());
		return manager;
	}

	private HttpSecurity determineHttpSecurityForEndpoint(HttpSecurity secInitial, String endpoint,
			InboundAuthorizationMode iam) throws Exception {

		HttpSecurity sec = secInitial;
		if (iam == InboundAuthorizationMode.BASIC) {
			System.err.println(String.format("Endpoint %s is BASIC authentication protected", endpoint));
			sec = sec.authorizeRequests().antMatchers(endpoint).authenticated().and();
		}
		// NB: Ignoring is not possible in this method; see configure(WebSecurity web)
		return sec;
	}

	@Override
	protected void configure(HttpSecurity security) throws Exception {
		if (!this.isInboundAuthSecurityEnabled()) {
			security.httpBasic().disable();
			return;
		}

		HttpSecurity sec = security;
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_DISCOVERY, this.promregatorConfiguration.getDiscovery().getAuth());
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_SINGLE_ENDPOINT_SCRAPING, this.promregatorConfiguration.getEndpoint().getAuth());
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/**", this.promregatorConfiguration.getEndpoint().getAuth());
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS, this.promregatorConfiguration.getMetrics().getAuth());
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_CACHE_INVALIDATION, this.promregatorConfiguration.getCache().getInvalidate().getAuth());

		
		// see also https://github.com/spring-projects/spring-security/issues/4242
		security.requestCache().requestCache(this.newHttpSessionRequestCache());
		
		// see also
		// https://www.boraji.com/spring-security-4-http-basic-authentication-example
		sec.httpBasic();
	}

	private WebSecurity determineWebSecurityForEndpoint(WebSecurity secInitial, String endpoint, InboundAuthorizationMode iam) {

		WebSecurity sec = secInitial;
		if (iam == InboundAuthorizationMode.NONE) {
			System.err.println(String.format("Endpoint %s is NOT authentication protected", endpoint));
			sec = sec.ignoring().antMatchers(endpoint).and();
		}
		return sec;
	}

	// see also
	// https://stackoverflow.com/questions/30366405/how-to-disable-spring-security-for-particular-url
	@Override
	public void configure(WebSecurity webInitial) throws Exception {
		if (!this.isInboundAuthSecurityEnabled()) {
			return;
		}

		WebSecurity web = webInitial;

		web = this.determineWebSecurityForEndpoint(web, EndpointConstants.ENDPOINT_PATH_DISCOVERY, this.promregatorConfiguration.getDiscovery().getAuth());
		web = this.determineWebSecurityForEndpoint(web, EndpointConstants.ENDPOINT_PATH_SINGLE_ENDPOINT_SCRAPING, this.promregatorConfiguration.getEndpoint().getAuth());
		web = this.determineWebSecurityForEndpoint(web, EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/**", this.promregatorConfiguration.getEndpoint().getAuth());
		web = this.determineWebSecurityForEndpoint(web, EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS, this.promregatorConfiguration.getMetrics().getAuth());
		this.determineWebSecurityForEndpoint(web, EndpointConstants.ENDPOINT_PATH_CACHE_INVALIDATION, this.promregatorConfiguration.getCache().getInvalidate().getAuth());

	}

	/*
	 * see issue with https://github.com/spring-projects/spring-security/issues/4242
	 */
	private RequestCache newHttpSessionRequestCache() {
		HttpSessionRequestCache httpSessionRequestCache = new HttpSessionRequestCache();
		httpSessionRequestCache.setCreateSessionAllowed(false);
		return httpSessionRequestCache;
	}
}
