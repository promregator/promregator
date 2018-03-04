package org.cloudfoundry.promregator.rewrite;

import java.util.LinkedList;
import java.util.List;

public class CFMetricFamilySamplesEnricher extends AbstractMetricFamilySamplesEnricher {
	public static final String LABELNAME_ORGNAME = "org_name";
	public static final String LABELNAME_SPACENAME = "space_name";
	public static final String LABELNAME_APPNAME = "app_name";
	public static final String LABELNAME_INSTANCEID = "instanceId";
	
	private static String[] labelNames = new String[] { LABELNAME_ORGNAME, LABELNAME_SPACENAME, LABELNAME_APPNAME, LABELNAME_INSTANCEID };
	
	public static String[] getEnrichingLabelNames() {
		return labelNames;
	}
	
	private String orgName;
	private String spaceName;
	private String appName;
	private String instance;

	public CFMetricFamilySamplesEnricher(String orgName, String spaceName, String appName, String instance) {
		this.instance = instance;
		this.spaceName = spaceName;
		this.appName = appName;
		this.orgName = orgName;
	}
	
	@Override
	protected List<String> getEnrichedLabelNames(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add(LABELNAME_ORGNAME);
		clone.add(LABELNAME_SPACENAME);
		clone.add(LABELNAME_APPNAME);
		clone.add(LABELNAME_INSTANCEID);
		
		return clone;
	}
	
	@Override
	protected List<String> getEnrichedLabelValues(List<String> original) {
		LinkedList<String> clone = new LinkedList<String>(original);
		
		clone.add(this.orgName);
		clone.add(this.spaceName);
		clone.add(this.appName);
		clone.add(this.instance);
		
		return clone;
	}
}
