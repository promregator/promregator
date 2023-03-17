package org.cloudfoundry.promregator.websecurity;

import java.util.UUID;

import org.cloudfoundry.promregator.config.InboundAuthorizationMode;
import org.cloudfoundry.promregator.endpoint.EndpointConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

	@Value("${promregator.discovery.auth:NONE}")
	private InboundAuthorizationMode discoveryAuth;

	@Value("${promregator.endpoint.auth:NONE}")
	private InboundAuthorizationMode endpointAuth;

	@Value("${promregator.metrics.auth:NONE}")
	private InboundAuthorizationMode promregatorMetricsAuth;

	@Value("${promregator.cache.invalidate.auth:NONE}")
	private InboundAuthorizationMode cacheInvalidateAuth;

	private boolean isInboundAuthSecurityEnabled() {
		if (this.discoveryAuth != InboundAuthorizationMode.NONE)
			return true;

		if (this.endpointAuth != InboundAuthorizationMode.NONE)
			return true;

		if (this.promregatorMetricsAuth != InboundAuthorizationMode.NONE)
			return true;

		if (this.cacheInvalidateAuth != InboundAuthorizationMode.NONE)
			return true;

		return false;
	}

	@Value("${promregator.authentication.basic.username:promregator}")
	private String basicAuthUsername;

	@Value("${promregator.authentication.basic.password:#{null}}")
	private String basicAuthPassword;

	private boolean securityDebug = false;

	@Bean
	public BCryptPasswordEncoder bCryptPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean(name = BeanIds.AUTHENTICATION_MANAGER)
	/* 
	 * see also https://stackoverflow.com/questions/21633555/how-to-inject-authenticationmanager-using-java-configuration-in-a-custom-filter
	 * and https://www.baeldung.com/spring-deprecated-websecurityconfigureradapter
	 */
	public AuthenticationManager authenticationManagerBean(HttpSecurity http, UserDetailsService userDetailsService, BCryptPasswordEncoder bCryptPasswordEncoder) throws Exception {
		 return http.getSharedObject(AuthenticationManagerBuilder.class)
				.userDetailsService(userDetailsService)
				.passwordEncoder(bCryptPasswordEncoder)
				.and()
				.build();
	}

	/*
	 * see also
	 * https://www.boraji.com/spring-security-4-http-basic-authentication-example
	 * and https://stackoverflow.com/questions/46999940/spring-boot-passwordencoder-error
	 */
	@Bean
	public UserDetailsService userDetailsService(BCryptPasswordEncoder bCryptPasswordEncoder) {
		InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

		String password = this.basicAuthPassword;
		if (password == null) {
			password = UUID.randomUUID().toString();

			// NB: Logging does not work here properly yet; better use stderr
			System.err.println();
			System.err.println(String.format("Using generated password for user %s: %s", this.basicAuthUsername, password));
			System.err.println();
		}

		manager.createUser(User.withUsername(this.basicAuthUsername)
				.password(bCryptPasswordEncoder.encode(password))
				.roles("USER")
				.build());
		return manager;
	}

	private HttpSecurity determineHttpSecurityForEndpoint(HttpSecurity secInitial, String endpoint,
			InboundAuthorizationMode iam) throws Exception {

		HttpSecurity sec = secInitial;
		if (iam == InboundAuthorizationMode.BASIC) {
			System.err.println(String.format("Endpoint %s is BASIC authentication protected", endpoint));
			sec = sec.authorizeHttpRequests().requestMatchers(endpoint).authenticated().and();
		} else {
			sec = sec.authorizeHttpRequests().requestMatchers(endpoint).permitAll().and();
		}
		return sec;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		if (!this.isInboundAuthSecurityEnabled()) {
			http.httpBasic().disable();
			return http.build();
		}

		HttpSecurity sec = http;
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_DISCOVERY, this.discoveryAuth);
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING + "/**", this.endpointAuth);
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS, this.promregatorMetricsAuth);
		sec = this.determineHttpSecurityForEndpoint(sec, EndpointConstants.ENDPOINT_PATH_CACHE_INVALIDATION, this.cacheInvalidateAuth);

		// see also https://github.com/spring-projects/spring-security/issues/4242
		http.requestCache().requestCache(this.newHttpSessionRequestCache());

		// see also https://www.boraji.com/spring-security-4-http-basic-authentication-example
		sec.httpBasic();
		
		return http.build();
	}

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return web -> web.debug(securityDebug);
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
