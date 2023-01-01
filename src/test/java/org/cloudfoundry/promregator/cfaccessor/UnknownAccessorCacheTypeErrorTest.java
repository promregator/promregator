package org.cloudfoundry.promregator.cfaccessor;

import org.junit.jupiter.api.Test;

public class UnknownAccessorCacheTypeErrorTest {

	@Test
	public void testCanBeCreated() {
		new UnknownAccessorCacheTypeError("unittest");
	}

}
