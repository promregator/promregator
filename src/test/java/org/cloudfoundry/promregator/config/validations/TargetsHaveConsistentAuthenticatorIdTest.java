package org.cloudfoundry.promregator.config.validations;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.config.TargetAuthenticatorConfiguration;
import org.junit.Assert;
import org.junit.jupiter.api.Test;


public class TargetsHaveConsistentAuthenticatorIdTest {

	@Test
	public void testValidateConfigBrokenNotExistingAtAll() {
		Target t = new Target();
		t.setAuthenticatorId("unittest");
		
		List<Target> targets = new LinkedList<>();
		targets.add(t);
		
		PromregatorConfiguration pc = new PromregatorConfiguration();
		pc.setTargets(targets);
		
		TargetsHaveConsistentAuthenticatorId subject = new TargetsHaveConsistentAuthenticatorId();
		String result = subject.validate(pc);
		
		Assert.assertNotNull(result);
	}
	
	@Test
	public void testValidateConfigBrokenWithWrongTAC() {
		Target t = new Target();
		t.setAuthenticatorId("unittest");
		
		List<Target> targets = new LinkedList<>();
		targets.add(t);
		
		
		TargetAuthenticatorConfiguration tac = new TargetAuthenticatorConfiguration();
		tac.setId("unittestOtherIdentifier");
		List<TargetAuthenticatorConfiguration> tacs = new LinkedList<>();
		tacs.add(tac);
		
		PromregatorConfiguration pc = new PromregatorConfiguration();
		pc.setTargets(targets);
		pc.setTargetAuthenticators(tacs);
		
		TargetsHaveConsistentAuthenticatorId subject = new TargetsHaveConsistentAuthenticatorId();
		String result = subject.validate(pc);
		
		Assert.assertNotNull(result);
	}
	
	@Test
	public void testValidateConfigOk() {
		Target t = new Target();
		t.setAuthenticatorId("unittest");
		
		List<Target> targets = new LinkedList<>();
		targets.add(t);
		
		
		TargetAuthenticatorConfiguration tac = new TargetAuthenticatorConfiguration();
		tac.setId("unittest");
		List<TargetAuthenticatorConfiguration> tacs = new LinkedList<>();
		tacs.add(tac);
		
		PromregatorConfiguration pc = new PromregatorConfiguration();
		pc.setTargets(targets);
		pc.setTargetAuthenticators(tacs);
		
		TargetsHaveConsistentAuthenticatorId subject = new TargetsHaveConsistentAuthenticatorId();
		String result = subject.validate(pc);
		
		Assert.assertNull(result);
	}

}
