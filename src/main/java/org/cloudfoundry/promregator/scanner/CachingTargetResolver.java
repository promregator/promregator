package org.cloudfoundry.promregator.scanner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.beans.factory.annotation.Value;

public class CachingTargetResolver implements TargetResolver {
	@Value("${cf.cache.timeout.resolver:300}")
	private int timeoutCacheResolverLevel;

	private TargetResolver parentTargetResolver;
	
	private PassiveExpiringMap<Target, List<ResolvedTarget>> targetResolutionCache;
	
	public CachingTargetResolver(TargetResolver parentTargetResolver) {
		this.parentTargetResolver = parentTargetResolver;
	}
	
	@PostConstruct
	public void setupCache() {
		/* Note that this cannot be done during construction as
		 * this.timeoutCacheResolverLevel isn't available there, yet.
		 */
		
		this.targetResolutionCache = new PassiveExpiringMap<>(this.timeoutCacheResolverLevel, TimeUnit.SECONDS);
	}

	public TargetResolver getNativeTargetResolver() {
		return parentTargetResolver;
	}

	@Override
	public List<ResolvedTarget> resolveTargets(List<Target> configTargets) {
		LinkedList<Target> toBeLoaded = new LinkedList<Target>();
		
		LinkedList<ResolvedTarget> result = new LinkedList<ResolvedTarget>();
		
		for (Target configTarget : configTargets) {
			List<ResolvedTarget> cached = this.targetResolutionCache.get(configTarget);
			if (cached != null) {
				result.addAll(cached);
			} else {
				toBeLoaded.add(configTarget);
			}
		}
		
		if (!toBeLoaded.isEmpty()) {
			List<ResolvedTarget> newlyResolvedTargets = this.parentTargetResolver.resolveTargets(toBeLoaded);
			
			result.addAll(newlyResolvedTargets);
			
			updateTargetResolutionCache(newlyResolvedTargets);
		}
		
		return result;
	}

	private void updateTargetResolutionCache(List<ResolvedTarget> newlyResolvedTargets) {
		HashMap<Target, LinkedList<ResolvedTarget>> map = new HashMap<>();
		for (ResolvedTarget rtarget : newlyResolvedTargets) {
			LinkedList<ResolvedTarget> list = map.get(rtarget.getOriginalTarget());
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
