package org.cloudfoundry.promregator.websecurity;

import java.util.UUID;

import org.cloudfoundry.promregator.config.InboundAuthorizationMode;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
	@Bean(name = BeanIds.AUTHENTICATION_MANAGER)
	@Override
	// see also
	// https://stackoverflow.com/questions/21633555/how-to-inject-authenticationmanager-using-java-configuration-in-a-custom-filter
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}

	@Value("${promregator.discovery.auth:NONE}")
	private InboundAuthorizationMode discoveryAuth;

	@Value("${promregator.endpoint.auth:NONE}")
	private InboundAuthorizationMode endpointAuth;

	@Value("${promregator.metrics.auth:NONE}")
	private InboundAuthorizationMode promregatorMetricsAuth;

	
	private boolean isInboundAuthSecurityEnabled() {
		if (this.discoveryAuth != InboundAuthorizationMode.NONE)
			return true;

		if (this.endpointAuth != InboundAuthorizationMode.NONE)
			return true;

		if (this.promregatorMetricsAuth != InboundAuthorizationMode.NONE)
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

	private HttpSecurity determineHttpSecurityForEndpoint(HttpSecurity secInitial, String endpoint, InboundAuthorizationMode iam) throws Exception {
		
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
		sec = this.determineHttpSecurityForEndpoint(sec, "/discovery", this.discoveryAuth);
		sec = this.determineHttpSecurityForEndpoint(sec, "/metrics", this.endpointAuth);
		sec = this.determineHttpSecurityForEndpoint(sec, "/singleTargetMetrics/**", this.endpointAuth);
		sec = this.determineHttpSecurityForEndpoint(sec, "/promregatorMetrics", this.promregatorMetricsAuth);

		// see also
		// https://www.boraji.com/spring-security-4-http-basic-authentication-example
		sec.httpBasic();
	}

	private WebSecurity determineWebSecurityForEndpoint(WebSecurity secInitial, String endpoint, InboundAuthorizationMode iam) throws Exception {
		
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
		
		web = this.determineWebSecurityForEndpoint(web, "/discovery", this.discoveryAuth);
		web = this.determineWebSecurityForEndpoint(web, "/metrics", this.endpointAuth);
		web = this.determineWebSecurityForEndpoint(web, "/singleTargetMetrics/**", this.endpointAuth);
		web = this.determineWebSecurityForEndpoint(web, "/promregatorMetrics", this.promregatorMetricsAuth);

	}
}
