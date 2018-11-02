package org.cloudfoundry.promregator.rewrite;

import java.util.LinkedList;
import java.util.List;

public class CFInstanceOnlyMetricFamilySamplesEnricher extends AbstractMetricFamilySamplesEnricher {
	public static final String LABELNAME_INSTANCE = "instance"; // see also https://github.com/prometheus/docs/pull/1190#issuecomment-431713406
	
	private static String[] labelNames = new String[] { LABELNAME_INSTANCE };
	
	public static String[] getEnrichingLabelNames() {
		return labelNames.clone();
	}
	
	private String instanceId;

	public CFInstanceOnlyMetricFamilySamplesEnricher(String instanceId) {
		this.instanceId = instanceId;
	}
	
	@Override
	protected List<String> getEnrichedLabelNames(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		for (int i = 0;i< labelNames.length; i++) {
			clone.add(labelNames[i]);
		}
		
		return clone;
	}
	
	@Override
	public List<String> getEnrichedLabelValues(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add(this.instanceId); // for LABELNAME_INSTANCE
		
		return clone;
	}

}
