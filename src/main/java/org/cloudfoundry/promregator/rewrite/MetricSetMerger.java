package org.cloudfoundry.promregator.rewrite;

import org.apache.logging.log4j.util.Strings;
import org.cloudfoundry.promregator.fetcher.FetchResult;

import io.prometheus.client.exporter.common.TextFormat;

public class MetricSetMerger {
	private static final String EOF_MARKER = "# EOF";
	private FetchResult fetchResult;
	private String additionalMetrics;

	public MetricSetMerger(FetchResult fetchResult, String additionalMetrics) {
		this.fetchResult = fetchResult;
		this.additionalMetrics = additionalMetrics;
	}
	
	public String merge() {
		if (this.fetchResult == null) {
			return this.additionalMetrics;
		}
		
		if (this.additionalMetrics == null || Strings.isEmpty(additionalMetrics)) {
			return this.fetchResult.data();
		}
		
		if (TextFormat.CONTENT_TYPE_004.equals(this.fetchResult.contentType())) {
			// that's simple, we may just concatenate
			return fetchResult.data()+additionalMetrics;
		}
		
		/*
		 * Warning! OpenMetrics-formatted response from the CFMetricsFetcher contain a "# EOF" at the end.
		 * Prometheus does consider this - if something is added after this indicator, Prometheus
		 * will reject the entire request as invalid.
		 * So, we need to properly handle this and cannot just concatentate...
		 */
		
		String metricSetData = fetchResult.data();
		
		metricSetData = trimTrailingNewLineChars(metricSetData);
		
		if (metricSetData.endsWith(EOF_MARKER)) {
			metricSetData = metricSetData.substring(0, metricSetData.length()-EOF_MARKER.length());
		}
		
		metricSetData = trimTrailingNewLineChars(metricSetData);
		
		additionalMetrics = trimTrailingNewLineChars(additionalMetrics);
		
		metricSetData += String.format("\n%s\n%s\n", additionalMetrics, EOF_MARKER);
		
		return metricSetData;
	}
	
	private static String trimTrailingNewLineChars(String s) {
		while (s.endsWith("\n") || s.endsWith("\r")) {
			s = s.substring(0, s.length()-1);
		}
		return s;
	}
}
