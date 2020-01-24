package org.cloudfoundry.promregator;

public final class ExitCodes {

	/* 
	 * Note: Exit codes 1-127 are reserved by JVM
	 * Exit codes 128-160 are reserved by JVM to return codes based on OS signals (see "kill")
	 * See also https://stackoverflow.com/a/21201431
	 * 161 seems to be the first free exit code...
	 */
	
	public static final int FAILED_WATCHDOG = 161;
	public static final int CF_ACCESSOR_OUT_OF_MEMORY = 162;
}
