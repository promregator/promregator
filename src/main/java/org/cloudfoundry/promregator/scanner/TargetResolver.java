package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.lite.config.CfTarget;

import java.util.List;

public interface TargetResolver {
	List<ResolvedTarget> resolveTargets(List<CfTarget> configTarget);
}
