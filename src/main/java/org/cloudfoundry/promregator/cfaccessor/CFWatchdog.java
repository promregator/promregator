package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.util.Optional;

import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.promregator.ExitCodes;
import org.cloudfoundry.promregator.config.CloudFoundryConfiguration;
import org.cloudfoundry.promregator.internalmetrics.InternalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class CFWatchdog {
	private static final Logger log = LoggerFactory.getLogger(CFWatchdog.class);
	
	private static final GetInfoResponse ERRONEOUS_GET_INFO_RESPONSE = GetInfoResponse.builder().apiVersion("FAILED").build();
	private final CloudFoundryConfiguration cf;
	private final CFApiClients cfApiClients;
	private final InternalMetrics internalMetrics;

	public CFWatchdog(CloudFoundryConfiguration cf, CFApiClients cfApiClients, InternalMetrics internalMetrics) {
		this.cf = cf;
		this.cfApiClients = cfApiClients;
		this.internalMetrics = internalMetrics;
	}



	@Scheduled(fixedRateString = "${cf.watchdog.rate:60}000", initialDelayString = "${cf.watchdog.initialDelay:60}000")
	@SuppressWarnings("unused")
	private void connectionWatchdog() {
		// see also https://github.com/promregator/promregator/issues/83
		
		if (!this.cf.getWatchdog().getEnabled()) {
			return;
		}

		cf.getApi().forEach((api, apiConfig) -> {
			this.cfApiClients.check(api)
					.timeout(Duration.ofMillis(this.cf.getWatchdog().getTimeout()))
					.doOnError(e -> {
						log.warn("Woof woof! It appears that the connection to the Cloud Controller is gone (api: "+api+"). Trying to restart Cloud Foundry Client", e);
						/*
						 * We might want to call further reactor commands with "block()" included.
						 * However, we can't do so in a doOnError() method. That is why we have to signal an error
						 * via a special return value and do so in a consumer.
						 */
						this.internalMetrics.countConnectionWatchdogReconnect();

						if (this.cf.getWatchdog().getRestartCount() != null) {
							final int restartCount = this.cf.getWatchdog().getRestartCount();

							if (this.internalMetrics.getCountConnectionWatchdogReconnect() > restartCount) {
								this.triggerApplicationRestartIfRequested();
							}

						}
					})
					.onErrorReturn(ERRONEOUS_GET_INFO_RESPONSE)
					.subscribe(response -> {
						if (response == ERRONEOUS_GET_INFO_RESPONSE) {
							// Note that there is no method at this.cloudFoundryClient, which would permit closing the old client
							this.cfApiClients.reset(api);
						}
					});
		});

	}
	
	@SuppressFBWarnings(value = "DM_EXIT", justification="Restart of JVM is done intentionally here!")
	private void triggerApplicationRestartIfRequested() {
		log.warn("Number of failed reset attempts has exceeded the threshold. Enforcing restart of application!");
		System.exit(ExitCodes.FAILED_WATCHDOG);
	}

}
