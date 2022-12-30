package org.cloudfoundry.promregator.cfaccessor;

public enum RequestType {
	ORG("org", "retrieveOrgId"),
	ALL_ORGS("allOrgs", "retrieveAllOrgIds"),
	SPACE("space", "retrieveSpaceId"),
	SPACE_IN_ORG("space", "retrieveAllSpaceIdsInOrg"),
	ALL_APPS_IN_SPACE("allApps", "retrieveAllApplicationIdsInSpace"),
	SPACE_SUMMARY("spaceSummary", "retrieveSpaceSummary"),
	DOMAINS("domains", "retrieveDomains"),	
	PROCESSES("processes", "retrieveProcesses"), // TODO V3: Documentation of new Request Type
	OTHER("other", "other"); // used for unit testing only
	
	private final String metricName;
	private final String loggerSuffix;

	private RequestType(String metricName, String loggerSuffix) {
		this.metricName = metricName;
		this.loggerSuffix = loggerSuffix;
	}

	/**
	 * @return the metricName
	 */
	public String getMetricName() {
		return metricName;
	}

	/**
	 * @return the loggerSuffix
	 */
	public String getLoggerSuffix() {
		return loggerSuffix;
	}
	

}
