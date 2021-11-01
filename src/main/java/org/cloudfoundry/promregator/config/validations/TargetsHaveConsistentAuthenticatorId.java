package org.cloudfoundry.promregator.config.validations;

import java.util.HashSet;

import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.lite.config.Target;
import org.cloudfoundry.promregator.lite.config.TargetAuthenticatorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * All targets must have an authenticatorId (if specified), which is specified
 * in the list of targetAuthenticators (referential integrity).
 *
 */
@Component
public class TargetsHaveConsistentAuthenticatorId implements ConfigurationValidation {
	private static final Logger log = LoggerFactory.getLogger(TargetsHaveConsistentAuthenticatorId.class);

	@Override
	public String validate(PromregatorConfiguration promregatorConfiguration) {
		boolean broken = false;

		final HashSet<String> authenticatorIds = new HashSet<>();

		log.debug("The following target authenticators are registered");
		for (TargetAuthenticatorConfiguration tac : promregatorConfiguration.getTargetAuthenticators()) {
			log.debug(tac.getId());
			
			authenticatorIds.add(tac.getId());
		}

		for (Target target : promregatorConfiguration.getTargets()) {
			if (target.getAuthenticatorId() == null) {
				continue; // not necessary to check
			}

			if (!authenticatorIds.contains(target.getAuthenticatorId())) {
				log.error(String.format(
						"Configuration error: Target %s refers to authenticator with identifier %s, but the latter does not exist",
						target.toString(), target.getAuthenticatorId()));
				broken = true;
			}
		}

		if (broken) {
			return "At least one target refers to an authenticator which does not exist.";
		}
		
		return null;
	}

}
