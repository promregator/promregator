package org.cloudfoundry.promregator.scanner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

@Component
public class CachingTargetResolver implements TargetResolver {
	private static final Logger log = Logger.getLogger(CachingTargetResolver.class);
	
	@Value("${cf.cache.timeout.resolver:300}")
	private int timeoutCacheResolverLevel;

	@Autowired(required=false)
	private List<CachingTargetResolverRemovalListener> removalListeners;
	
	private TargetResolver nativeTargetResolver;
	
	private LoadingCache<Target, List<ResolvedTarget>> targetResolutionCache;
	
	public CachingTargetResolver(TargetResolver targetResolver) {
		this.nativeTargetResolver = targetResolver;
	}
	
	@PostConstruct
	public void setupCache() {
		/* Note that this cannot be done during construction as
		 * this.timeoutCacheResolverLevel isn't available there, yet.
		 */
		
		this.targetResolutionCache = CacheBuilder.newBuilder()
			.expireAfterWrite(this.timeoutCacheResolverLevel, TimeUnit.SECONDS)
			.removalListener(new RemovalListener<Target, List<ResolvedTarget>>() {
	
				@Override
				public void onRemoval(RemovalNotification<Target, List<ResolvedTarget>> notification) {
					if (removalListeners == null)
						return; // nothing to do
					
					// propagate to all registered listeners
					for (CachingTargetResolverRemovalListener listener : removalListeners) {
						listener.onRemoval(notification.getKey(), notification.getValue());
					}
				}
				
			})
			.build(new CacheLoader<Target, List<ResolvedTarget>>() {
	
				@Override
				public List<ResolvedTarget> load(Target key) throws Exception {
					// shouldn't be used, but as last resort, this is ok
					log.warn(String.format("Single-loading target %s, which is not efficient and thus should be avoided", key.toString()));
					
					List<Target> list = new LinkedList<>();
					list.add(key);
					
					return nativeTargetResolver.resolveTargets(list);
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

	public void invalidateCache() {
		this.targetResolutionCache.invalidateAll();
	}
}
