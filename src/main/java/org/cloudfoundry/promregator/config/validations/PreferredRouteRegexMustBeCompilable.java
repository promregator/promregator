package org.cloudfoundry.promregator.config.validations;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PreferredRouteRegexMustBeCompilable implements ConfigurationValidation {
	private static final Logger log = LoggerFactory.getLogger(PreferredRouteRegexMustBeCompilable.class);
	
	@Override
	public String validate(PromregatorConfiguration promregatorConfiguration) {
		boolean failed = false;
	
		for (Target target : promregatorConfiguration.getTargets()) {
			List<String> preferredRouteRegex = target.getPreferredRouteRegex();
			if (CollectionUtils.isEmpty(preferredRouteRegex)) {
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
