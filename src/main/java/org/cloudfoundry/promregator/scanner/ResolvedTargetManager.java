package org.cloudfoundry.promregator.scanner;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.stereotype.Component;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@Component
public class ResolvedTargetManager implements TargetResolver {
	private static final Logger log = Logger.getLogger(ResolvedTargetManager.class);
	
	private LoadingCache<Target, List<ResolvedTarget>> targetResolutionCache;
	
	private TargetResolver nativeTargetResolver;
	
	public ResolvedTargetManager(TargetResolver targetResolver) {
		this.nativeTargetResolver = targetResolver;
		
		this.targetResolutionCache = CacheBuilder.newBuilder()
			.expireAfterWrite(2L, TimeUnit.MINUTES)
			// TODO make this timeout customizable
			.build(new CacheLoader<Target, List<ResolvedTarget>>() {

				@Override
				public List<ResolvedTarget> load(Target key) throws Exception {
					return targetResolver.resolveTargets(key);
				}
			});
	}

	public TargetResolver getNativeTargetResolver() {
		return nativeTargetResolver;
	}

	@Override
	public List<ResolvedTarget> resolveTargets(Target configTarget) {
		try {
			List<ResolvedTarget> result = this.targetResolutionCache.get(configTarget);
			return result;
		} catch (ExecutionException e) {
			log.error(String.format("Error loading resolution of target %s", configTarget.toString()), e);
			return null;
		}
	}
}
