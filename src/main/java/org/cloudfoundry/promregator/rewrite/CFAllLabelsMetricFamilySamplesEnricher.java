package org.cloudfoundry.promregator.rewrite;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A MetricFamilySamplesEnricher which enriches the labels of metrics by 
 * - org_name
 * - space_name
 * - app_name
 * - cf_instance_id
 * 
 */
public class CFAllLabelsMetricFamilySamplesEnricher extends AbstractMetricFamilySamplesEnricher {
	public static final String LABELNAME_ORGNAME = "org_name";
	public static final String LABELNAME_SPACENAME = "space_name";
	public static final String LABELNAME_APPNAME = "app_name";
	public static final String LABELNAME_INSTANCEID = "cf_instance_id";
	
	/* still required for backward-compatibility
	 * Note that LABELNAME_INSTANCE is the better approach
	 */
	public static final String LABELNAME_INSTANCE_NUMBER = "cf_instance_number";
	
	private static String[] labelNames = new String[] { LABELNAME_ORGNAME, LABELNAME_SPACENAME, LABELNAME_APPNAME, LABELNAME_INSTANCEID, LABELNAME_INSTANCE_NUMBER };
	public static String[] getEnrichingLabelNames() {
		return labelNames.clone();
	}

	private String orgName;
	private String spaceName;
	private String appName;
	private String instanceId;

	public CFAllLabelsMetricFamilySamplesEnricher(String orgName, String spaceName, String appName, String instanceId) {
		this.orgName = orgName;
		this.spaceName = spaceName;
		this.appName = appName;
		this.instanceId = instanceId;
	}
	
	@Override
	protected List<String> getEnrichedLabelNames(List<String> original) {
		List<String> clone = new LinkedList<>(original);
		for(String label: labelNames) {
			clone.remove(label);
			//TODO: log that label was removed
		}
		Collections.addAll(clone, labelNames);
		return clone;
	}
	
	@Override
	public List<String> getEnrichedLabelValues(List<String> original) {
		List<String> clone = new LinkedList<>(original);
		clone.add(orgName);
		clone.add(spaceName);
		clone.add(appName);
		clone.add(instanceId);
		clone.add(getInstanceFromInstanceId(instanceId));
		return clone;
	}

	@Override
	protected List<String> getEnrichedLabelValues(List<String> originalLabelNames, List<String> originalLabelValues) {
		List<String> clone = new LinkedList<>(originalLabelValues);
		for(String label: labelNames) {
			if(originalLabelNames.contains(label)){
				clone.remove(originalLabelNames.indexOf(label));
				//TODO: log that value was removed
			}
		}
		return getEnrichedLabelValues(clone);
	}

	private static String getInstanceFromInstanceId(String instanceId) {
		if (instanceId == null)
			return null;
		
		int pos = instanceId.lastIndexOf(':');
		if (pos == -1)
			return null; // invalid format
		
		return instanceId.substring(pos+1);
	}
}
