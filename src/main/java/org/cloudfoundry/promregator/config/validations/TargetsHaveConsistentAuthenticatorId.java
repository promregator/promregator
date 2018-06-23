package org.cloudfoundry.promregator.config.validations;

import java.util.HashSet;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.config.TargetAuthenticatorConfiguration;

/**
 * All targets must have an authenticatorId (if specified), which is specified
 * in the list of targetAuthenticators (referential integrity).
 *
 */
public class TargetsHaveConsistentAuthenticatorId implements ConfigurationValidation {
	private static final Logger log = Logger.getLogger(TargetsHaveConsistentAuthenticatorId.class);

	@Override
	public String validate(PromregatorConfiguration promregatorConfiguration) {
		boolean broken = false;

		final HashSet<String> authenticatorIds = new HashSet<>();

		for (TargetAuthenticatorConfiguration tac : promregatorConfiguration.getTargetAuthenticators()) {
			authenticatorIds.add(tac.getId());
		}

		for (Target target : promregatorConfiguration.getTargets()) {
			if (target.getAuthenticatorId() == null) {
				continue; // not necessary to check
			}

			if (!authenticatorIds.contains(target.getAuthenticatorId())) {
				log.fatal(String.format(
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
