package org.cloudfoundry.promregator.config.validations;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.CommonConfigurationApplication;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;

public class PreferredRouteRegexMustBeCompilable implements ConfigurationValidation {
	private static final Logger log = Logger.getLogger(PreferredRouteRegexMustBeCompilable.class);
	
	@Override
	public String validate(PromregatorConfiguration promregatorConfiguration) {
		boolean failed = false;
	
		for (CommonConfigurationApplication target : promregatorConfiguration.getTargets()) {
			List<String> preferredRouteRegex = target.getPreferredRouteRegex();
			if (preferredRouteRegex == null) {
				continue;
			}
			
			for (String regex : preferredRouteRegex) {
				try {
					Pattern.compile(regex);
				} catch (PatternSyntaxException e) {
					log.error(String.format("There is a preferredRouteRegex which cannot be compiled: %s; please fix and restart", regex), e);
					failed = true;
				}
			}
		}
			
		if (failed) {
			return "There is at least one target configured, which has an invalid regular expression for configuration option 'preferredRouteRegex' set; fix it first!";
		}
		
		return null;
	}

}
