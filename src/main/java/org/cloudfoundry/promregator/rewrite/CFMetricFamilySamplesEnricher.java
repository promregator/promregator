package org.cloudfoundry.promregator.rewrite;

import java.util.LinkedList;
import java.util.List;

public class CFMetricFamilySamplesEnricher extends AbstractMetricFamilySamplesEnricher {
	public static final String LABELNAME_ORGNAME = "org_name";
	public static final String LABELNAME_SPACENAME = "space_name";
	public static final String LABELNAME_APPNAME = "app_name";
	public static final String LABELNAME_INSTANCEID = "instanceId";
	public static final String LABELNAME_INSTANCE = "instance";
	
	private static String[] labelNames = new String[] { LABELNAME_ORGNAME, LABELNAME_SPACENAME, LABELNAME_APPNAME, LABELNAME_INSTANCEID, LABELNAME_INSTANCE };
	
	public static String[] getEnrichingLabelNames() {
		return labelNames;
	}
	
	private String orgName;
	private String spaceName;
	private String appName;
	private String instanceId;

	public CFMetricFamilySamplesEnricher(String orgName, String spaceName, String appName, String instanceId) {
		this.instanceId = instanceId;
		this.spaceName = spaceName;
		this.appName = appName;
		this.orgName = orgName;
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
	protected List<String> getEnrichedLabelValues(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add(this.orgName);
		clone.add(this.spaceName);
		clone.add(this.appName);
		clone.add(this.instanceId);
		clone.add(getInstanceFromInstanceId(this.instanceId));
		
		return clone;
	}
	
	public static String getInstanceFromInstanceId(String instanceId) {
		if (instanceId == null)
			return null;
		
		int pos = instanceId.lastIndexOf(':');
		if (pos == -1)
			return null; // invalid format
		
		return instanceId.substring(pos+1);
	}
}
