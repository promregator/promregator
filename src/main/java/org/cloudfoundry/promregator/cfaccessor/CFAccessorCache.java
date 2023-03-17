package org.cloudfoundry.promregator.cfaccessor;

public interface CFAccessorCache extends CFAccessor {
	void invalidateCacheRoute();
	
	void invalidateCacheProcess();
	
	void invalidateCacheApplication();

	void invalidateCacheSpace();

	void invalidateCacheOrg();

	void invalidateCacheDomain();
}
