package org.cloudfoundry.promregator.rewrite;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class OwnMetricsEnrichmentLabelVector {
	public static final String LABELNAME_ORGNAME = "org_name";
	public static final String LABELNAME_SPACENAME = "space_name";
	public static final String LABELNAME_APPNAME = "app_name";
	public static final String LABELNAME_INSTANCEID = "cf_instance_id";

	/* still required for backward-compatibility
	 * Note that LABELNAME_INSTANCE is the better approach
	 */
	public static final String LABELNAME_INSTANCE_NUMBER = "cf_instance_number";

	
	private String[] labelNames;
	
	private String orgName;
	private String spaceName;
	private String appName;
	private String instanceId;

	public OwnMetricsEnrichmentLabelVector(String labelNamePrefix, String orgName, String spaceName, String appName, String instanceId) {
		this.instanceId = instanceId;
		this.spaceName = spaceName;
		this.appName = appName;
		this.orgName = orgName;
		
		if (labelNamePrefix == null) {
			this.labelNames = new String[] { LABELNAME_ORGNAME, LABELNAME_SPACENAME, LABELNAME_APPNAME, LABELNAME_INSTANCEID, LABELNAME_INSTANCE_NUMBER };
		} else {
			this.labelNames = new String[] { 
					labelNamePrefix + LABELNAME_ORGNAME, 
					labelNamePrefix + LABELNAME_SPACENAME, 
					labelNamePrefix + LABELNAME_APPNAME, 
					labelNamePrefix + LABELNAME_INSTANCEID, 
					labelNamePrefix + LABELNAME_INSTANCE_NUMBER 
			};
		}
	}

	
	public String[] getEnrichingLabelNames() {
		return labelNames.clone();
	}

	protected List<String> getEnrichedLabelNames(List<String> original) {
		List<String> clone = new LinkedList<>(original);
		Collections.addAll(clone, labelNames);
		
		return clone;
	}
	
	public List<String> getEnrichedLabelValues() {
		return this.getEnrichedLabelValues(Collections.emptyList());
	}
	
	public List<String> getEnrichedLabelValues(List<String> original) {
		List<String> clone = new LinkedList<>(original);
		
		clone.add(this.orgName);
		clone.add(this.spaceName);
		clone.add(this.appName);
		clone.add(this.instanceId);
		clone.add(getInstanceFromInstanceId(this.instanceId));
		
		return clone;
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
