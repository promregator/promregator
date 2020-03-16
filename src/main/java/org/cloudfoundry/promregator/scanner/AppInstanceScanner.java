package org.cloudfoundry.promregator.scanner;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.Null;

public interface AppInstanceScanner {

	/**
	 * determines a list of instances based on a provided list of targets.
	 * Note that there may be more or less instances than targets as input. This may
	 * have multiple reasons:
	 * - one target resolves to multiple instances (e.g. if an application has multiple
	 * instances running due to load balancing or fail-over safety)
	 * - one target does not resolve to any instance, if the application has been
	 * misconfigured or if the application is currently not running.
	 * @param targets the list of targets, for which the properties of instances shall be determined
	 * @param applicationIdFilter an optional filter function allowing to prefilter results early, indicating whether an application based on its applicationId is in scope or not
	 * @param instanceFilter an optional filter function allowing to prefilter results early, indicating whether an instance is in scope or not
	 * @return the list of instances containing the access URL and the instance identifier
	 */
	Mono<List<Instance>> determineInstancesFromTargets(List<ResolvedTarget> targets, @Null Predicate<? super String> applicationIdFilter, @Null Predicate<? super Instance> instanceFilter);
}
