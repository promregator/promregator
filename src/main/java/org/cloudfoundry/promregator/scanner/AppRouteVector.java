package org.cloudfoundry.promregator.scanner;

public class AppRouteVector {
	
	private String appId;
	private String routeId;
	private String domainId;
	private String hostname;
	private String path;
	private String domain;	
	private boolean internal;
	private int port;
	public String getAppId() {
		
		return appId;
	}
	public boolean isInternal() {
		return internal;
	}
	public void setInternal(boolean internal) {
		this.internal = internal;
	}
	public String getDomain() {
		return domain;
	}
	public void setDomain(String domain) {
		this.domain = domain;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public String getHostname() {
		return hostname;
	}
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	public String getDomainId() {
		return domainId;
	}
	public void setDomainId(String domainId) {
		this.domainId = domainId;
	}
	public String getRouteId() {
		return routeId;
	}
	public void setRouteId(String routeId) {
		this.routeId = routeId;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public void setAppId(String appId) {
		this.appId = appId;
	}
	public String getUrl() {
		// TODO check neither are null
		return String.format("%s.%s", hostname, domain);
	}
	
}
