package org.cloudfoundry.promregator.scanner;

import java.util.LinkedList;
import java.util.List;

import org.cloudfoundry.promregator.config.Target;

public class TrivialTargetResolver implements TargetResolver {

	@Override
	public List<ResolvedTarget> resolveTargets(List<Target> configTargets) {
		// As of now this is trivial, as we do not have any resolution available
		
		List<ResolvedTarget> result = new LinkedList<>();
		final Target emptyTarget = new Target();
		for (Target configTarget : configTargets) {
			ResolvedTarget rt = new ResolvedTarget();
			
			rt.setOrgName(configTarget.getOrgName());
			rt.setSpaceName(configTarget.getSpaceName());
			rt.setApplicationName(configTarget.getApplicationName());
			rt.setPath(configTarget.getPathOrDefault());
			rt.setProtocol(configTarget.getProtocolOrDefault());
			rt.setOriginalTarget(emptyTarget);
			
			result.add(rt);
		}
		
		return result;
	}

}
