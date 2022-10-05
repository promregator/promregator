package org.cloudfoundry.promregator.scanner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class RouteUtils {
	private static final Logger log = LoggerFactory.getLogger(RouteUtils.class);
	
	/* TODO bring under Unit test */
	public static String determineApplicationRoute(final List<String> urls, final List<Pattern> patterns) {
		if (urls == null || urls.isEmpty()) {
			log.debug("No URLs provided to determine ApplicationURL with");
			return null;
		}

		if (CollectionUtils.isEmpty(patterns)) {
			log.debug("No Preferred Route URL (Regex) provided; taking first Application Route in the list provided");
			return urls.get(0);
		}

		for (Pattern pattern : patterns) {
			for (String url : urls) {
				log.debug(String.format("Attempting to match Application Route '%s' against pattern '%s'", url,
						pattern.toString()));
				Matcher m = pattern.matcher(url);
				if (m.matches()) {
					log.debug(String.format("Match found, using Application Route '%s'", url));
					return url;
				}
			}
		}

		// if we reach this here, then we did not find any match in the regex.
		// The fallback then is the old behavior by returned just the first-guess
		// element
		log.debug(String.format(
				"Though Preferred Router URLs were provided, no route matched; taking the first route as fallback (compatibility!), which is '%s'",
				urls.get(0)));
		return urls.get(0);
	}
	
	public static String formatAccessURL(final String protocol, final String hostnameDomain, final String path) {
		final String applicationUrl = String.format("%s://%s", protocol, hostnameDomain);
		log.debug(String.format("Using Application URL: '%s'", applicationUrl));

		String applUrl = applicationUrl;
		if (!applicationUrl.endsWith("/")) {
			applUrl += '/';
		}

		String internalPath = path;
		while (internalPath.startsWith("/")) {
			internalPath = internalPath.substring(1);
		}

		return applUrl + internalPath;
	}

	public static String formatInternalAccessURL(final String hostnameDomain, final String path, final int defaultInternalRoutePort, final int internalRoutePort,
			final int instanceId) {
		int port = internalRoutePort;
		if(port == 0) {
			port = defaultInternalRoutePort;
		}
		
		String internalURL = String.format("%s.%s:%s", instanceId, hostnameDomain, port);
		log.debug(String.format("Using internal Application URL: '%s'", internalURL));

		return formatAccessURL("http", internalURL, path);
	}

}
