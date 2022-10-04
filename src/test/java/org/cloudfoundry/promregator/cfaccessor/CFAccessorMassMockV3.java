package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.Random;

import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.spaces.GetSpaceSummaryResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v3.spaces.GetSpaceResponse;
import org.junit.jupiter.api.Assertions;

import reactor.core.publisher.Mono;

public class CFAccessorMassMockV3 implements CFAccessor {
	public static final String UNITTEST_ORG_UUID = "eb51aa9c-2fa3-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SPACE_UUID = "db08be9a-2fa4-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_APP_UUID_PREFIX = "55820b2c-2fa5-11e8-b467-";
	public static final String UNITTEST_SHARED_DOMAIN_UUID = "be9b8696-2fa6-11e8-b467-0ed5f89f718b";
	public static final String UNITTEST_SHARED_DOMAIN = "shared.domain.example.org";
	public static final String UNITTEST_ROUTE_UUID = "b79d2d45-6f6c-4f9e-bd5b-1a7b3ccac247";
	
	public static final String CREATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	public static final String UPDATED_AT_TIMESTAMP = "2014-11-24T19:32:49+00:00";
	
	private Random randomGen = new Random();
	
	private int amountInstances;
	
	public CFAccessorMassMockV3(int amountInstances) {
		super();
		this.amountInstances = amountInstances;
	}

	private Duration getSleepRandomDuration() {
		return Duration.ofMillis(this.randomGen.nextInt(250));
	}

	@Override
	public Mono<ListOrganizationsResponse> retrieveOrgId(String orgName) {
		Assertions.fail("retrieveOrgId called in a V3 test environment");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceId(String orgId, String spaceName) {
		Assertions.fail("trieveSpaceId called in a V3 test environment");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListApplicationsResponse> retrieveAllApplicationIdsInSpace(String orgId, String spaceId) {
		Assertions.fail("retrieveAllApplicationIdsInSpace called in a V3 test environment");
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Mono<GetSpaceSummaryResponse> retrieveSpaceSummary(String spaceId) {
		Assertions.fail("retrieveSpaceSummary called in a V3 test environment");
		throw new UnsupportedOperationException();
	}

	public Mono<ListOrganizationsResponse> retrieveAllOrgIds() {
		Assertions.fail("retrieveAllOrgIds called in a V3 test environment");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListSpacesResponse> retrieveSpaceIdsInOrg(String orgId) {
		Assertions.fail("retrieveSpaceIdsInOrg called in a V3 test environment");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<GetInfoResponse> getInfo() {
		GetInfoResponse data = GetInfoResponse.builder()
				.description("CFAccessorMassMockV3")
				.name("CFAccessorMassMockV3")
				.version(1)
				.build();
		
		return Mono.just(data);
	}

	@Override
	public void reset() {
		// nothing to be done
	}

	@Override
	public Mono<ListOrganizationDomainsResponse> retrieveAllDomains(String orgId) {
		Assertions.fail("retrieveAllDomains called in a V3 test environment");
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveOrgIdV3(String orgName) {
		// TODO to be implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationsResponse> retrieveAllOrgIdsV3() {
		// TODO to be implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdV3(String orgId, String spaceName) {
		// TODO to be implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.spaces.ListSpacesResponse> retrieveSpaceIdsInOrgV3(String orgId) {
		// TODO to be implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.applications.ListApplicationsResponse> retrieveAllApplicationsInSpaceV3(String orgId, String spaceId) {
		// TODO to be implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<GetSpaceResponse> retrieveSpaceV3(String spaceId) {
		// TODO to be implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<org.cloudfoundry.client.v3.organizations.ListOrganizationDomainsResponse> retrieveAllDomainsV3(String orgId) {
		// TODO to be implemented
		throw new UnsupportedOperationException();
	}

	@Override
	public Mono<ListApplicationRoutesResponse> retrieveRoutesForAppId(String appId) {
		// TODO needs to be clarified
		Assertions.fail("TODO CAN THIS BE CALLED IN V3 CONTEXT AS WELL?");
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isV3Enabled() {
		return true;
	}
}
