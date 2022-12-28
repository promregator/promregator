package org.cloudfoundry.promregator.rewrite;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
			if(clone.indexOf(label) < 0) {
				clone.add(label);
			} else {
				//TODO: log that label already existed
			}
		}
		return clone;
	}

	@Override
	public List<String> getEnrichedLabelValues(List<String> originalLabelNames, List<String> originalLabelValues) {
		List<String> clone = new LinkedList<>(originalLabelValues);
		boolean orgNameExists = false;
		boolean spaceNameExists = false;
		boolean appNameExists = false;
		boolean instanceIdExists = false;
		boolean instanceNumberExists = false;

		for(String label: labelNames) {
			int index = originalLabelNames.indexOf(label);
			if(index > -1){
				switch (label){
					case LABELNAME_ORGNAME:
						clone.set(index, orgName);
						orgNameExists = true;
						//TODO: log that value already existed
						break;
					case LABELNAME_SPACENAME:
						clone.set(index, spaceName);
						spaceNameExists = true;
						//TODO: log that value already existed
						break;
					case LABELNAME_APPNAME:
						clone.set(index, appName);
						appNameExists = true;
						//TODO: log that value already existed
						break;
					case LABELNAME_INSTANCEID:
						clone.set(index, instanceId);
						instanceIdExists = true;
						//TODO: log that value already existed
						break;
					case LABELNAME_INSTANCE_NUMBER:
						clone.set(index, getInstanceFromInstanceId(instanceId));
						instanceNumberExists = true;
						//TODO: log that value already existed
						break;
				}
			}
		}

		if(!orgNameExists) {clone.add(orgName);}
		if(!spaceNameExists) {clone.add(spaceName);}
		if(!appNameExists){clone.add(appName);}
		if(!instanceIdExists){clone.add(instanceId);}
		if(!instanceNumberExists){clone.add(getInstanceFromInstanceId(instanceId));}

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
