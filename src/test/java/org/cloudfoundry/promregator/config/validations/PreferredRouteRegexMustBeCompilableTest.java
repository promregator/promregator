package org.cloudfoundry.promregator.config.validations;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PreferredRouteRegexMustBeCompilableTest {

	@Test
	public void testNullPreferredRouteRegexDoesNotCrash() {
		PreferredRouteRegexMustBeCompilable subject = new PreferredRouteRegexMustBeCompilable();
		PromregatorConfiguration promregatorConfiguration = new PromregatorConfiguration();
		List<Target> targets = new LinkedList<>();
		Target target = new Target();
		target.setPreferredRouteRegex(null);
		targets.add(target);
		promregatorConfiguration.setTargets(targets);
		
		Assertions.assertNull(subject.validate(promregatorConfiguration));
	}
	
	@Test
	public void testValidDoesNotBreak() {
		PreferredRouteRegexMustBeCompilable subject = new PreferredRouteRegexMustBeCompilable();
		PromregatorConfiguration promregatorConfiguration = new PromregatorConfiguration();
		List<Target> targets = new LinkedList<>();
		Target target = new Target();
		List<String> preferredRouteRegex = new LinkedList<>();
		preferredRouteRegex.add("dummy");
		target.setPreferredRouteRegex(preferredRouteRegex);
		targets.add(target);
		promregatorConfiguration.setTargets(targets);
		
		Assertions.assertNull(subject.validate(promregatorConfiguration));
	}
	
	@Test
	public void testInvalidRaisesError() {
		PreferredRouteRegexMustBeCompilable subject = new PreferredRouteRegexMustBeCompilable();
		PromregatorConfiguration promregatorConfiguration = new PromregatorConfiguration();
		List<Target> targets = new LinkedList<>();
		Target target = new Target();
		List<String> preferredRouteRegex = new LinkedList<>();
		preferredRouteRegex.add("[");
		target.setPreferredRouteRegex(preferredRouteRegex);
		targets.add(target);
		promregatorConfiguration.setTargets(targets);
		
		Assertions.assertNotNull(subject.validate(promregatorConfiguration));
	}

}
