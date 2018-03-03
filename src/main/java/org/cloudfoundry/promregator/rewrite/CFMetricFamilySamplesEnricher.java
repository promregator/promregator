package org.cloudfoundry.promregator.rewrite;

import java.util.LinkedList;
import java.util.List;

public class CFMetricFamilySamplesEnricher extends AbstractMetricFamilySamplesEnricher {
	private static final String LABELNAME_ORGNAME = "org_name";
	private static final String LABELNAME_SPACENAME = "space_name";
	private static final String LABELNAME_APPNAME = "app_name";
	private static final String LABELNAME_INSTANCE = "instance";
	
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
		clone.add(LABELNAME_INSTANCE);
		
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
