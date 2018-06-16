package org.cloudfoundry.promregator.scanner;

import java.util.List;

import org.cloudfoundry.promregator.config.Target;

public interface TargetResolver {
	List<ResolvedTarget> resolveTargets(List<Target> configTarget);
}
