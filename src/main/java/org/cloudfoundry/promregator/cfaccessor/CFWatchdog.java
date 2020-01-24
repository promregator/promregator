package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.Optional;

import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

public class CFWatchdog {
	private static final Logger log = LoggerFactory.getLogger(CFWatchdog.class);
	
	private static final GetInfoResponse ERRONEOUS_GET_INFO_RESPONSE = GetInfoResponse.builder().apiVersion("FAILED").build();
	
	@Autowired
	private CFAccessor mainCFAccessor;
	
	@Autowired
	private InternalMetrics internalMetrics;
	
	@Value("${cf.watchdog.enabled:false}")
	private boolean watchdogEnabled = false;

	@Value("${cf.watchdog.timeout:2500}")
	private int watchdogTimeoutInMS = 2500;
	
	@Value("${cf.watchdog.restartCount:#{null}}")
	private Optional<Integer> watchdogRestartCount;
	
	@Scheduled(fixedRateString = "${cf.watchdog.rate:60}000", initialDelayString = "${cf.watchdog.initialDelay:60}000")
	@SuppressWarnings("unused")
	private void connectionWatchdog() {
		// see also https://github.com/promregator/promregator/issues/83
		
		if (!this.watchdogEnabled) {
			return;
		}
		
		this.mainCFAccessor.getInfo()
			.timeout(Duration.ofMillis(this.watchdogTimeoutInMS))
			.doOnError(e -> {
				log.warn("Woof woof! It appears that the connection to the Cloud Controller is gone. Trying to restart Cloud Foundry Client", e);
				/* 
				 * We might want to call further reactor commands with "block()" included.
				 * However, we can't do so in a doOnError() method. That is why we have to signal an error
				 * via a special return value and do so in a consumer.
				 */
				this.internalMetrics.countConnectionWatchdogReconnect();
				
				if (this.watchdogRestartCount != null && this.watchdogRestartCount.isPresent()) {
					final int restartCount = this.watchdogRestartCount.get();
					
					if (this.internalMetrics.getCountConnectionWatchdogReconnect() > restartCount) {
						this.triggerApplicationRestartIfRequested();
					}
					
				}
			})
			.onErrorReturn(ERRONEOUS_GET_INFO_RESPONSE)
			.subscribe(response -> {
				if (response == ERRONEOUS_GET_INFO_RESPONSE) {
					// Note that there is no method at this.cloudFoundryClient, which would permit closing the old client
					this.mainCFAccessor.reset();
				}
			});
	}
	
	/* 
	 * Note: Exit codes 1-127 are reserved by JVM
	 * Exit codes 128-160 are reserved by JVM to return codes based on OS signals (see "kill")
	 * See also https://stackoverflow.com/a/21201431
	 * 161 seems to be the first free exit code...
	 */
	private static final int EXITCODE_FAILED_WATCHDOG = 161;
	
	private void triggerApplicationRestartIfRequested() {
		log.warn("Number of failed reset attempts has exceeded the threshold. Enforcing restart of application!");
		System.exit(EXITCODE_FAILED_WATCHDOG);
	}

}
