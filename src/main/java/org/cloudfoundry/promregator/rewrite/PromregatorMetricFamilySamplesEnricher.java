package org.cloudfoundry.promregator.rewrite;

import java.util.LinkedList;
import java.util.List;

public class PromregatorMetricFamilySamplesEnricher extends AbstractMetricFamilySamplesEnricher {

	private static final String LABEL_PROMREGATOR = "promregator";

	public PromregatorMetricFamilySamplesEnricher() {
	}
	
	@Override
	protected List<String> getEnrichedLabelNames(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add(LABEL_PROMREGATOR);
		
		return clone;
	}
	
	@Override
	protected List<String> getEnrichedLabelValues(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add("true");
		
		return clone;
	}
}
