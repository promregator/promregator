package org.cloudfoundry.promregator.cfaccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.http.conn.util.InetAddressUtils;
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
		return this.cloudFoundryClient.organizations().list(orgsRequest).log("Query Org");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSpaceId(java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
		return this.cloudFoundryClient.spaces().list(spacesRequest).log("Query Space");
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
		return this.cloudFoundryClient.applicationsV2().list(request).log("Query App");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRouteMapping(java.lang.String)
	 */
	@Override
	public Mono<ListRouteMappingsResponse> retrieveRouteMapping(String appId) {
		ListRouteMappingsRequest mappingRequest = ListRouteMappingsRequest.builder().applicationId(appId).build();
		return this.cloudFoundryClient.routeMappings().list(mappingRequest).log("Query Route Mapping");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveRoute(java.lang.String)
	 */
	@Override
	public Mono<GetRouteResponse> retrieveRoute(String routeId) {
		GetRouteRequest getRequest = GetRouteRequest.builder().routeId(routeId).build();
		return this.cloudFoundryClient.routes().get(getRequest).log("Get Route");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveSharedDomain(java.lang.String)
	 */
	@Override
	public Mono<GetSharedDomainResponse> retrieveSharedDomain(String domainId) {
		GetSharedDomainRequest domainRequest = GetSharedDomainRequest.builder().sharedDomainId(domainId).build();
		return this.cloudFoundryClient.sharedDomains().get(domainRequest).log("Get Domain");
	}
	
	/* (non-Javadoc)
	 * @see org.cloudfoundry.promregator.cfaccessor.CFAccessor#retrieveProcesses(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Mono<ListProcessesResponse> retrieveProcesses(String orgId, String spaceId, String appId) {
		ListProcessesRequest request = ListProcessesRequest.builder().organizationId(orgId).spaceId(spaceId).applicationId(appId).build();
		return this.cloudFoundryClient.processes().list(request).log("List Processes");
	}
}
