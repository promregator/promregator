package org.cloudfoundry.promregator.rewrite;

import java.util.LinkedList;
import java.util.List;

/**
 * A NullMetricFamilySamplesEnricher is an enricher which does not enrich anything.
 * Usually, this enricher is being used for enriching metrics which have been scraped (and full enrichment is disabled
 * in configuration).
 */
public class NullMetricFamilySamplesEnricher extends AbstractMetricFamilySamplesEnricher {
	public static String[] getEnrichingLabelNames() {
		return new String[0];
	}
	
	public NullMetricFamilySamplesEnricher() {
		/* 
		 * This block is left blank intentionally, as nothing needs to be done 
		 */
	}
	
	@Override
	protected List<String> getEnrichedLabelNames(List<String> original) {
		return new LinkedList<>(original);
	}
	
	@Override
	public List<String> getEnrichedLabelValues(List<String> original) {
		return new LinkedList<>(original);
	}

}
