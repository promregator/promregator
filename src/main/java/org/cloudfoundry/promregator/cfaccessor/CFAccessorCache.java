package org.cloudfoundry.promregator.cfaccessor;

public interface CFAccessorCache extends CFAccessor {
	public void invalidateCacheApplications();
	
	public void invalidateCacheSpace();

	public void invalidateCacheOrg();

}
