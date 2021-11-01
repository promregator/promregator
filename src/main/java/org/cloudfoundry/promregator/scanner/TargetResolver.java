package org.cloudfoundry.promregator.scanner;

import org.cloudfoundry.promregator.lite.config.Target;

import java.util.List;

public interface TargetResolver {
	List<ResolvedTarget> resolveTargets(List<Target> configTarget);
}
