package org.cloudfoundry.promregator.scanner;

import java.util.List;

import org.cloudfoundry.promregator.config.Target;
import reactor.core.publisher.Mono;

public interface TargetResolver {
	Mono<List<ResolvedTarget>> resolveTargets(List<Target> configTarget);
}
