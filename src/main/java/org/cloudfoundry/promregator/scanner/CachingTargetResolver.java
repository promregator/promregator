package org.cloudfoundry.promregator.scanner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.cloudfoundry.promregator.config.Target;
import org.springframework.stereotype.Component;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@Component
public class CachingTargetResolver implements TargetResolver {
	private LoadingCache<Target, List<ResolvedTarget>> targetResolutionCache;
	
	private TargetResolver nativeTargetResolver;
	
	public CachingTargetResolver(TargetResolver targetResolver) {
		this.nativeTargetResolver = targetResolver;
		
		this.targetResolutionCache = CacheBuilder.newBuilder()
			.expireAfterWrite(2L, TimeUnit.MINUTES)
			// TODO make this timeout customizable
			// TODO register Removal Listeners --> remove metrics from global CollectorRegistry
			.build(new CacheLoader<Target, List<ResolvedTarget>>() {

				@Override
				public List<ResolvedTarget> load(Target key) throws Exception {
					List<Target> list = new LinkedList<>();
					list.add(key);
					
					return targetResolver.resolveTargets(list);
				}
			});
	}

	public TargetResolver getNativeTargetResolver() {
		return nativeTargetResolver;
	}

	@Override
	public List<ResolvedTarget> resolveTargets(List<Target> configTargets) {
		LinkedList<Target> toBeLoaded = new LinkedList<Target>();
		
		LinkedList<ResolvedTarget> result = new LinkedList<ResolvedTarget>();
		
		for (Target configTarget : configTargets) {
			List<ResolvedTarget> cached = this.targetResolutionCache.getIfPresent(configTarget);
			if (cached != null) {
				result.addAll(cached);
			} else {
				toBeLoaded.add(configTarget);
			}
		}
		
		if (!toBeLoaded.isEmpty()) {
			List<ResolvedTarget> newlyResolvedTargets = this.nativeTargetResolver.resolveTargets(toBeLoaded);
			
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
}
