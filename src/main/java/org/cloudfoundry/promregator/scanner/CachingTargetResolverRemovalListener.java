package org.cloudfoundry.promregator.scanner;

import java.util.List;

import org.cloudfoundry.promregator.config.Target;

public interface CachingTargetResolverRemovalListener {
	void onRemoval(Target removedConfigTarget, List<ResolvedTarget> removedResolvedTargets);
}
