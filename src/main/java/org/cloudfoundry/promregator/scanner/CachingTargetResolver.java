package org.cloudfoundry.promregator.scanner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.log4j.Logger;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.cloudfoundry.promregator.springconfig.JMSSpringConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;

public class CachingTargetResolver implements TargetResolver {
	private static final Logger log = Logger.getLogger(CachingTargetResolver.class);

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
		
		/* see also issue #75: the list here might include duplicates, which we need to eliminate */
		HashSet<ResolvedTarget> hset = new HashSet<>(result);
		return new LinkedList<>(hset);
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
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_ORG_CREATED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForCreateOrg(String spaceId) {
		log.info(String.format("Cache invalidated due to CF event on newly created org"));
		this.invalidateCache();
	}

	@JmsListener(destination=MessageBusDestination.CF_EVENT_ORG_DELETED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForDeleteOrg() {
		log.info(String.format("Cache invalidated due to CF event on deletion of an org"));
		this.invalidateCache();
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_ORG_CHANGED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForChangedOrg() {
		log.info(String.format("Cache invalidated due to CF event on change of an org"));
		this.invalidateCache();
	}

	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_SPACE_CREATED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForCreateSpace() {
		log.info(String.format("Cache invalidated due to CF event on newly created space"));
		this.invalidateCache();
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_SPACE_DELETED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForDeleteSpace() {
		log.info(String.format("Cache invalidated due to CF event on deletion of a space"));
		this.invalidateCache();
	}
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_SPACE_CHANGED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForChangeSpace() {
		log.info(String.format("Cache invalidated due to CF event on change of a space"));
		this.invalidateCache();
	}
	
	
	
	@JmsListener(destination=MessageBusDestination.CF_EVENT_APP_CREATED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForCreateApp() {
		log.info(String.format("Cache invalidated due to CF event on newly created application"));
		this.invalidateCache();
	}

	/* Note that CF_EVENT_APP_CHANGED event is not covered here! We only need to know about a name change */
	@JmsListener(destination=MessageBusDestination.CF_EVENT_APP_NAME_CHANGED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForApp() {
		log.info(String.format("Cache invalidated due to CF event on name change of an application"));
		this.invalidateCache();
	}

	@JmsListener(destination=MessageBusDestination.CF_EVENT_APP_DELETED, containerFactory=JMSSpringConfiguration.BEAN_NAME_JMS_LISTENER_CONTAINER_FACTORY)
	public void invalidateCacheForDeleteApp() {
		log.info(String.format("Cache invalidated due to CF event on a deleted application"));
		this.invalidateCache();
	}
	
}
