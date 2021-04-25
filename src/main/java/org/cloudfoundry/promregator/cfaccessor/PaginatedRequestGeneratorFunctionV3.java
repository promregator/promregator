package org.cloudfoundry.promregator.cfaccessor;

import org.cloudfoundry.client.v3.PaginatedRequest;

@FunctionalInterface
public interface PaginatedRequestGeneratorFunctionV3<T extends PaginatedRequest> {
	// for the idea, see also https://stackoverflow.com/a/27872395 
	T apply(int resultsPerPage, int pageNumber);
}
