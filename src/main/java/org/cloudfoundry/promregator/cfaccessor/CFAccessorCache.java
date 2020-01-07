package org.cloudfoundry.promregator.cfaccessor;

public interface CFAccessorCache extends CFAccessor {
	void invalidateCacheApplications();

	void invalidateCacheSpace();

	void invalidateCacheOrg();

}
