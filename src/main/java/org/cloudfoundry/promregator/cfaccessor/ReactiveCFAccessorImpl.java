package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsRequest;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsResponse;
import org.cloudfoundry.client.v2.routes.GetRouteRequest;
import org.cloudfoundry.client.v2.routes.GetRouteResponse;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainRequest;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.processes.ListProcessesRequest;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext.Builder;
import org.cloudfoundry.reactor.ProxyConfiguration;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class ReactiveCFAccessorImpl implements CFAccessor {
	private static final Logger log = Logger.getLogger(ReactiveCFAccessorImpl.class);
	
	@Value("${cf.api_host}")
	private String apiHost;

	@Value("${cf.username}")
	private String username;
	
	@Value("${cf.password}")
	private String password;
	
	@Value("${cf.skipSslValidation:false}")
	private boolean skipSSLValidation;
	
	@Value("${cf.proxyHost:#{null}}") 
	private String proxyHost;
	
	@Value("${cf.proxyPort:0}") 
	private int proxyPort;

	private static final Pattern PATTERN_HTTP_BASED_PROTOCOL_PREFIX = Pattern.compile("^https?://");
	
	private ReactorCloudFoundryClient cloudFoundryClient;
	
	private DefaultConnectionContext connectionContext(ProxyConfiguration proxyConfiguration) throws ConfigurationException {
		if (apiHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(apiHost).find()) {
			throw new ConfigurationException("cf.api_host configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}

		Builder connctx = DefaultConnectionContext.builder().apiHost(apiHost).skipSslValidation(skipSSLValidation);
		
		if (proxyConfiguration != null) {
			connctx = connctx.proxyConfiguration(proxyConfiguration);
		}
		return connctx.build();
	}

	private PasswordGrantTokenProvider tokenProvider() {
		return PasswordGrantTokenProvider.builder().password(this.password).username(this.username).build();
	}

	private ProxyConfiguration proxyConfiguration() throws ConfigurationException {
		if (this.proxyHost != null && PATTERN_HTTP_BASED_PROTOCOL_PREFIX.matcher(this.proxyHost).find()) {
			throw new ConfigurationException("cf.proxyHost configuration parameter must not contain an http(s)://-like prefix; specify the hostname only instead");
		}
		
		if (this.proxyHost != null && this.proxyPort != 0) {
			
			String proxyIP = null;
			if (!InetAddressUtils.isIPv4Address(this.proxyHost) && !InetAddressUtils.isIPv6Address(this.proxyHost)) {
				/*
				 * NB: There is currently a bug in io.netty.util.internal.SocketUtils.connect()
				 * which is called implicitly by the CF API Client library, which leads to the effect
				 * that a hostname for the proxy isn't resolved. Thus, it is only possible to pass 
				 * IP addresses as proxy names.
				 * To work around this issue, we manually perform a resolution of the hostname here
				 * and then feed that one to the CF API Client library...
				 */
				try {
					InetAddress ia = InetAddress.getByName(this.proxyHost);
					proxyIP = ia.getHostAddress();
				} catch (UnknownHostException e) {
					throw new ConfigurationException("The cf.proxyHost provided cannot be resolved to an IP address; is there a typo in your configuration?", e);
				}
			} else {
				// the address specified is already an IP address
				proxyIP = this.proxyHost;
			}
			
			return ProxyConfiguration.builder().host(proxyIP).port(this.proxyPort).build();
			
		} else {
			return null;
		}
	}
	
	private ReactorCloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorCloudFoundryClient.builder().connectionContext(connectionContext).tokenProvider(tokenProvider).build();
	}
	
	@PostConstruct
	@SuppressWarnings("PMD.UnusedPrivateMethod") // method is really required
	private void constructCloudFoundryClient() throws ConfigurationException {
		ProxyConfiguration proxyConfiguration = this.proxyConfiguration();
		DefaultConnectionContext connectionContext = this.connectionContext(proxyConfiguration);
		PasswordGrantTokenProvider tokenProvider = this.tokenProvider();
		
		this.cloudFoundryClient = this.cloudFoundryClient(connectionContext, tokenProvider);
	}

	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveOrgId(java.lang.String)
	 */
	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
		Mono<ListOrganizationsResponse> monoResp = this.cloudFoundryClient.organizations().list(orgsRequest);
		
		monoResp = monoResp.log(log.getName()+".retrieveOrgId", Level.FINE);
		
		return monoResp;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceId(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
		Mono<ListSpacesResponse> monoResp = this.cloudFoundryClient.spaces().list(spacesRequest);
		
		monoResp = monoResp.log(log.getName()+".retrieveSpaceId", Level.FINE);
		
		return monoResp;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveApplicationId(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListApplicationsResponse> retrieveApplicationId(String orgId, String spaceId, String applicationName) {
		ListApplicationsRequest request = ListApplicationsRequest.builder()
				.organizationId(orgId)
				.spaceId(spaceId)
				.name(applicationName)
				.build();
		Mono<ListApplicationsResponse> monoResp = this.cloudFoundryClient.applicationsV2().list(request);
		
		monoResp = monoResp.log(log.getName()+".retrieveApplicationId", Level.FINE);
		
		return monoResp;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRouteMapping(java.lang.String)
	 */
	@Override
	public Mono<ListRouteMappingsResponse> retrieveRouteMapping(String appId) {
		ListRouteMappingsRequest mappingRequest = ListRouteMappingsRequest.builder().applicationId(appId).build();
		Mono<ListRouteMappingsResponse> monoResp = this.cloudFoundryClient.routeMappings().list(mappingRequest);
		
		monoResp = monoResp.log(log.getName()+".retrieveRouteMapping", Level.FINE);
		
		return monoResp;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRoute(java.lang.String)
	 */
	@Override
	public Mono<GetRouteResponse> retrieveRoute(String routeId) {
		GetRouteRequest getRequest = GetRouteRequest.builder().routeId(routeId).build();
		Mono<GetRouteResponse> monoResp = this.cloudFoundryClient.routes().get(getRequest).log();
		
		monoResp = monoResp.log(log.getName()+".retrieveRoute", Level.FINE);
		
		return monoResp;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSharedDomain(java.lang.String)
	 */
	@Override
	public Mono<GetSharedDomainResponse> retrieveSharedDomain(String domainId) {
		GetSharedDomainRequest domainRequest = GetSharedDomainRequest.builder().sharedDomainId(domainId).build();
		Mono<GetSharedDomainResponse> monoResp = this.cloudFoundryClient.sharedDomains().get(domainRequest).log();
		
		monoResp = monoResp.log(log.getName()+".retrieveSharedDomain", Level.FINE);
		
		return monoResp;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveProcesses(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListProcessesResponse> retrieveProcesses(String orgId, String spaceId, String appId) {
		ListProcessesRequest request = ListProcessesRequest.builder().organizationId(orgId).spaceId(spaceId).applicationId(appId).build();
		Mono<ListProcessesResponse> monoResp = this.cloudFoundryClient.processes().list(request);
		
		monoResp = monoResp.log(log.getName()+".retrieveProcesses", Level.FINE);
		
		return monoResp;
	}
}
