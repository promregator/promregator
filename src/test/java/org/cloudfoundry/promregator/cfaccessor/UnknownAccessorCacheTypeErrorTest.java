package org.cloudfoundry.promregator.cfaccessor;

import org.junit.jupiter.api.Test;

class UnknownAccessorCacheTypeErrorTest {

	@Test
	void testCanBeCreated() {
		new UnknownAccessorCacheTypeError("unittest");
	}

}
