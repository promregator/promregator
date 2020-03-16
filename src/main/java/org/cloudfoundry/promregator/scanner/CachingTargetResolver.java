package org.cloudfoundry.promregator.scanner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;

public class CachingTargetResolver implements TargetResolver {

	private TargetResolver parentTargetResolver;
	
	private PassiveExpiringMap<Target, List<ResolvedTarget>> targetResolutionCache;
	
	public CachingTargetResolver(TargetResolver parentTargetResolver,
								 @Value("${cf.cache.timeout.resolver:300}") int timeoutCacheResolverLevel) {
		this.parentTargetResolver = parentTargetResolver;
		this.targetResolutionCache = new PassiveExpiringMap<>(timeoutCacheResolverLevel, TimeUnit.SECONDS);
	}
	
	@Override
	public Mono<List<ResolvedTarget>> resolveTargets(List<Target> configTargets) {
		List<Target> toBeLoaded = new LinkedList<>();
		
		List<ResolvedTarget> result = new LinkedList<>();
		
		for (Target configTarget : configTargets) {
			List<ResolvedTarget> cached = this.targetResolutionCache.get(configTarget);
			if (cached != null) {
				result.addAll(cached);
			} else {
				toBeLoaded.add(configTarget);
			}
		}
		
		if (!toBeLoaded.isEmpty()) {
			return this.parentTargetResolver.resolveTargets(toBeLoaded)
					.map(newResolvedTargets -> {
						result.addAll(newResolvedTargets);

						updateTargetResolutionCache(newResolvedTargets);

						/* see also issue #75: the list here might include duplicates, which we need to eliminate */
						HashSet<ResolvedTarget> hset = new HashSet<>(result);
						return new LinkedList<>(hset);
					});
		} else {
			/* see also issue #75: the list here might include duplicates, which we need to eliminate */
			HashSet<ResolvedTarget> hset = new HashSet<>(result);
			return Mono.just(new LinkedList<>(hset));
		}
	}

	private void updateTargetResolutionCache(List<ResolvedTarget> newlyResolvedTargets) {
		Map<Target, List<ResolvedTarget>> map = new HashMap<>();
		for (ResolvedTarget rtarget : newlyResolvedTargets) {
			List<ResolvedTarget> list = map.get(rtarget.getOriginalTarget());
			if (list == null) {
				list = new LinkedList<>();
			}
			list.add(rtarget);
			map.put(rtarget.getOriginalTarget(), list);
		}
		
		this.targetResolutionCache.putAll(map);
	}

	public void invalidateCache() {
		this.targetResolutionCache.clear();
	}
}
