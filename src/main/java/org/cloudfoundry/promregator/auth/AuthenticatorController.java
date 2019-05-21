package org.cloudfoundry.promregator.auth;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.cloudfoundry.promregator.config.AuthenticatorConfiguration;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.config.TargetAuthenticatorConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This class controls the target-dependent set of AuthenticationEnrichers
 */
public class AuthenticatorController {
	private final Map<String, AuthenticationEnricher> mapping = new HashMap<String, AuthenticationEnricher>();

	@Autowired
	private PromregatorConfiguration promregatorConfiguration;

	private AuthenticationEnricher globalAuthenticationEnricher;

	@PostConstruct
	@SuppressWarnings("unused")
	private void determineGlobalAuthenticationEnricher() {
		AuthenticatorConfiguration authConfig = promregatorConfiguration.getAuthenticator();
		if (authConfig == null) {
			this.globalAuthenticationEnricher = new NullEnricher();
			return;
		}
		
		this.globalAuthenticationEnricher =	AuthenticationEnricherFactory.create(authConfig);
	}
	
	@PostConstruct
	@SuppressWarnings("unused")
	private void loadMapFromConfiguration() {
		for (TargetAuthenticatorConfiguration tac : this.promregatorConfiguration.getTargetAuthenticators()) {
			String id = tac.getId();
			AuthenticationEnricher ae = AuthenticationEnricherFactory.create(tac);

			if (ae != null) {
				this.mapping.put(id, ae);
			}
		}
	}

	/**
	 * retrieves the AuthenticationEnricher identified by its ID (note: this is
	 * *NOT* the target!)
	 * 
	 * @param authId
	 *            the authenticatorId as specified in the configuration definition
	 * @return the AuthenticationEnricher, which is associated to the given
	 *         identifier, or <code>null</code> in case that no such
	 *         AuthenticationEnricher exists.
	 */
	public AuthenticationEnricher getAuthenticationEnricherById(String authId) {
		return this.mapping.get(authId);
	}

	/**
	 * retrieves the target-specific AuthenticationEnricher for a given target
	 * @param target the target for which its AuthenticationEnricher shall be retrieved
	 * @return the AuthenticationEnricher for the target, which was requested. If there is no
	 * target-specific AuthenticationEnricher defined for the target, a fallback to the globally 
	 * defined AuthenticationEnricher is performed. 
	 */
	public AuthenticationEnricher getAuthenticationEnricherByTarget(Target target) {
		AuthenticationEnricher ae = null;
		
		if (target == null) {
			return this.globalAuthenticationEnricher;
		}
		
		String authId = target.getAuthenticatorId();
		if (authId != null) {
			ae = this.getAuthenticationEnricherById(authId);
			if (ae != null) {
				return ae;
			}
		}
		
		// fallback
		return this.globalAuthenticationEnricher;
	}
	
}
