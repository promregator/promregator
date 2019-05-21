package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v2.OrderDirection;
import org.cloudfoundry.client.v2.PaginatedRequest;

@FunctionalInterface
public interface PaginatedRequestGeneratorFunction<T extends PaginatedRequest> {
	// for the idea, see also https://stackoverflow.com/a/27872395 
	T apply(OrderDirection orderDirection, int resultsPerPage, int pageNumber);
}
