package org.cloudfoundry.promregator.scanner;

public class ScannerUtils {

	public static String determineAccessURL(final String applicationUrl, final String path) {
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

}
