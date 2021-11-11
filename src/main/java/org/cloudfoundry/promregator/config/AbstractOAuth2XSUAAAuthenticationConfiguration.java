package org.cloudfoundry.promregator.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AbstractOAuth2XSUAAAuthenticationConfiguration {

	protected String client_id;

	protected final Set<String> scopes = new HashSet<>();

	public String getClient_id() {
		return client_id;
	}

	public void setClient_id(String client_id) {
		this.client_id = client_id;
	}

	public Set<String> getScopes() {
		return new HashSet<>(this.scopes);
	}

	public void setScopes(String scopes) {
		setScopes(new HashSet<>(Arrays.asList(scopes.split("[\\s,]+"))));
	}

	public void setScopes(Set<String> scopes) {
		this.scopes.clear();
		this.scopes.addAll(scopes);
	}
}
