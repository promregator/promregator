package org.cloudfoundry.promregator.cfaccessor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UnknownAccessorCacheTypeErrorTest {

	@Test
	public void testCanBeCreated() {
		Assertions.assertNotNull(new UnknownAccessorCacheTypeError("unittest"));
	}

}
