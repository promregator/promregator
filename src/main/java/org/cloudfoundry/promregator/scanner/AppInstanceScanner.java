package org.cloudfoundry.promregator.scanner;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsRequest;
import org.cloudfoundry.client.v2.routemappings.ListRouteMappingsResponse;
import org.cloudfoundry.client.v2.routemappings.RouteMappingResource;
import org.cloudfoundry.client.v2.routes.GetRouteRequest;
import org.cloudfoundry.client.v2.routes.GetRouteResponse;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainRequest;
import org.cloudfoundry.client.v2.shareddomains.GetSharedDomainResponse;
import org.cloudfoundry.client.v3.applications.ApplicationResource;
import org.cloudfoundry.client.v3.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v3.organizations.OrganizationResource;
import org.cloudfoundry.client.v3.processes.ListProcessesRequest;
import org.cloudfoundry.client.v3.processes.ListProcessesResponse;
import org.cloudfoundry.client.v3.processes.ProcessResource;
import org.cloudfoundry.client.v3.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.spaces.SpaceResource;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppInstanceScanner {
	@Autowired
	private ReactorCloudFoundryClient cloudFoundryClient;
	
	private static PassiveExpiringMap<String, String> orgMap = new PassiveExpiringMap<String, String>(60, TimeUnit.MINUTES);
	private static PassiveExpiringMap<String, String> spaceMap = new PassiveExpiringMap<String, String>(60, TimeUnit.MINUTES);
	private static PassiveExpiringMap<String, String> applicationMap = new PassiveExpiringMap<String, String>(5, TimeUnit.MINUTES);
	private static PassiveExpiringMap<String, String> hostnameMap = new PassiveExpiringMap<String, String>(5, TimeUnit.MINUTES);
	
	private String getOrgId(String orgName) {
		String cached = orgMap.get(orgName);
		
		if (cached == null) {
			ListOrganizationsRequest orgsRequest = ListOrganizationsRequest.builder().name(orgName).build();
			ListOrganizationsResponse orgsList = this.cloudFoundryClient.organizationsV3().list(orgsRequest).block();
			for (OrganizationResource organizationResource : orgsList.getResources()) {
				cached = organizationResource.getId();
				orgMap.put(orgName, cached);
			}
		}
		
		return cached;
	}

	private String getSpaceId(String orgId, String spaceName) {
		final String key = orgId+"|"+spaceName;
		String cached = spaceMap.get(key);
		
		if (cached == null) {
			ListSpacesRequest spacesRequest = ListSpacesRequest.builder().organizationId(orgId).name(spaceName).build();
			ListSpacesResponse spacesList = this.cloudFoundryClient.spacesV3().list(spacesRequest).block();
			
			for (SpaceResource spaceResource : spacesList.getResources()) {
				cached = spaceResource.getId();
				spaceMap.put(key, cached);
			}
		}
		
		return cached;
	}
	
	private String getApplicationId(String orgId, String spaceId, String applicationName) {
		final String key = orgId+"|"+spaceId+"|"+applicationName;
		
		String cached = applicationMap.get(key);
		
		if (cached == null) {
			ListApplicationsRequest request = ListApplicationsRequest.builder().organizationId(orgId).spaceId(spaceId).name(applicationName).build();
			ListApplicationsResponse response = this.cloudFoundryClient.applicationsV3().list(request).block();
			
			for (ApplicationResource applicationResource : response.getResources()) {
				cached =applicationResource.getId();
				applicationMap.put(key, cached);
			}
		}
		
		return cached;
	}
	
	/**
	 * returns the set of instance Ids (including app identifiers) for a given app in a space of an org
	 * @param orgName the organization name in which the CF app is located.
	 * @param spaceName the space name in which the CF app is located
	 * @param appName the name of the app for which the instance ids shall be returned.
	 * @return a set of instance ids as strings in the format <i><app uuid>:<instanceid</i>. Alternatively an empty set is returned, 
	 * if the app could not be found or no instance is running.
	 */
	public Set<String> getInstanceIds(String orgName, String spaceName, String appName) {
		HashSet<String> result = new HashSet<String>();
		
		String orgId = this.getOrgId(orgName);
		if (orgId == null)
			return result;
		
		String spaceId = this.getSpaceId(orgId, spaceName);
		if (spaceId == null)
			return result;
		
		String appId = this.getApplicationId(orgId, spaceId, appName);
		if (appId == null) 
			return result;
		
		ListProcessesRequest request = ListProcessesRequest.builder().organizationId(orgId).spaceId(spaceId).applicationId(appId).build();
		ListProcessesResponse processList = this.cloudFoundryClient.processes().list(request).block();
		
		if (processList == null)
			return result;
		
		for (ProcessResource processResource : processList.getResources()) {
			int numberOfInstances = processResource.getInstances();
			for (int i = 0; i< numberOfInstances; i++) {
				result.add(String.format("%s:%d", appId, i));
			}
		}
		
		return result;
	}
	
	/**
	 * analyses all route bindings for a CF app and returns the first full-qualified hostname with which the app can be reached
	 * @param orgName the organization name in which the CF app is located.
	 * @param spaceName the space name in which the CF app is located
	 * @param appName the name of the app for which a hostname shall be returned.
	 * @return some (e.g. first) full-qualified hostname under which the app is reachable or <code>null</code> if no such one exists.
	 */
	public String getFirstHostname(String orgName, String spaceName, String appName) {
		final String key = orgName+"|"+spaceName+"|"+appName;
		
		String cached = hostnameMap.get(key);
		
		if (cached == null) {
			String orgId = this.getOrgId(orgName);
			String spaceId = this.getSpaceId(orgId, spaceName);
			String appId = this.getApplicationId(orgId, spaceId, appName);
			
			ListRouteMappingsRequest mappingRequest = ListRouteMappingsRequest.builder().applicationId(appId).build();
			ListRouteMappingsResponse routeMappingList = this.cloudFoundryClient.routeMappings().list(mappingRequest).block();
			
			String routeId = null;
			for (RouteMappingResource routeMappingResource : routeMappingList.getResources()) {
				routeId = routeMappingResource.getEntity().getRouteId();
				break;
			}
			
			if (routeId == null)
				return null;
			
			GetRouteRequest getRequest = GetRouteRequest.builder().routeId(routeId).build();
			GetRouteResponse routeResponse = this.cloudFoundryClient.routes().get(getRequest).block();
			
			if (routeResponse == null)
				return null;
			
			String host = routeResponse.getEntity().getHost();
			
			GetSharedDomainRequest domainRequest = GetSharedDomainRequest.builder().sharedDomainId(routeResponse.getEntity().getDomainId()).build();
			GetSharedDomainResponse domainResponse = this.cloudFoundryClient.sharedDomains().get(domainRequest).block();
			if (domainResponse == null)
				return null;
			
			String domain = domainResponse.getEntity().getName();
			
			cached = host+'.'+domain;
			hostnameMap.put(key, cached);
		}
		
		return cached;
	}

}
