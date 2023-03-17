package org.cloudfoundry.promregator.textformat004;

import org.cloudfoundry.promregator.fetcher.FetchResult;
import org.junit.jupiter.api.Assertions;

public class ParserCompareUtils {
	public static void compareFetchResult(FetchResult fetchResult, String metricsSetPart) {
		Assertions.assertTrue(fetchResult.data().contains(metricsSetPart));
	}
}
