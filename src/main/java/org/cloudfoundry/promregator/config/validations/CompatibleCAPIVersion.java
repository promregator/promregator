package org.cloudfoundry.promregator.config.validations;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.lite.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.lite.config.Target;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CompatibleCAPIVersion implements ConfigurationValidation {
	private final CFAccessor cfAccessor;

	public CompatibleCAPIVersion(CFAccessor cfAccessor) {
		this.cfAccessor = cfAccessor;
	}

	private boolean usingV3OnlyFeature(Target targetConfiguration) {
		return targetConfiguration.getKubernetesAnnotations();
	}

	@Override
	public String validate(PromregatorConfiguration promregatorConfiguration) {
		return promregatorConfiguration.getTargets().stream().anyMatch(this::usingV3OnlyFeature) &&
			!cfAccessor.isV3Enabled() ? "You have enabled features which require V3 CAPI support" : null;
	}
}
