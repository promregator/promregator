package org.cloudfoundry.promregator.config.validations;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.config.Target;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.when;

class CompatibleCAPIVersionTest {
	@InjectMocks
	private CompatibleCAPIVersion subject;

	@Mock
	private CFAccessor cfAccessorMock;

	@BeforeEach
	public void initMocks(){
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testValidateConfigNoV3() {
		Target t = new Target();
		t.setKubernetesAnnotations(true);

		List<Target> targets = new LinkedList<>();
		targets.add(t);

		PromregatorConfiguration pc = new PromregatorConfiguration();
		pc.setTargets(targets);

		when(this.cfAccessorMock.isV3Enabled()).thenReturn(false);
		String result = subject.validate(pc);

		Assertions.assertNotNull(result);
	}

	@Test
	void testValidateConfigNoV3NoKubernetes() {
		Target t = new Target();
		t.setKubernetesAnnotations(false);

		List<Target> targets = new LinkedList<>();
		targets.add(t);

		PromregatorConfiguration pc = new PromregatorConfiguration();
		pc.setTargets(targets);

		when(this.cfAccessorMock.isV3Enabled()).thenReturn(false);
		String result = subject.validate(pc);

		Assertions.assertNull(result);
	}

	@Test
	void testValidateConfigOk() {
		Target t = new Target();
		t.setKubernetesAnnotations(true);

		List<Target> targets = new LinkedList<>();
		targets.add(t);

		PromregatorConfiguration pc = new PromregatorConfiguration();
		pc.setTargets(targets);

		when(this.cfAccessorMock.isV3Enabled()).thenReturn(true);
		String result = subject.validate(pc);

		Assertions.assertNull(result);
	}
}
