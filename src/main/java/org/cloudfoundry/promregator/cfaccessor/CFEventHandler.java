package org.cloudfoundry.promregator.cfaccessor;

import java.time.Instant;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.events.EventEntity;
import org.cloudfoundry.client.v2.events.EventResource;
import org.cloudfoundry.client.v2.events.ListEventsResponse;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CFEventHandler {
	private static final Logger log = Logger.getLogger(CFEventHandler.class);
	
	@Autowired
	private CFAccessor cfAccessor;
	
	private Instant lastRefreshed = Instant.now().minusSeconds(60*60*24);
	
	@Scheduled(fixedDelay=10000)
	public void periodRequest() throws ConfigurationException {
		Instant now = Instant.now();
		
		Mono<ListEventsResponse> res = this.cfAccessor.retrieveEvents(this.lastRefreshed);
		res.doOnSuccess(r -> {
			log.debug(String.format("Starting to scan now; fetching events since %s", this.lastRefreshed.toString()));
			// ensure that we don't fetch the same stuff twice
			this.lastRefreshed = now;
		}).map(r -> r.getResources())
		.flatMapMany(list -> Flux.fromIterable(list))
		.doOnComplete(() -> {
			log.debug(String.format("Scan complete: new timestamp %s", this.lastRefreshed.toString()));
		}).subscribe(event -> {
			this.processEvent(event);
		});
	}

	private void processEvent(EventResource event) {
		EventEntity entity = event.getEntity();
		
		log.info(String.format("Event %s raised: type=%s on %s, happened to %s/%s/%s", event.getMetadata().getId(), 
				entity.getType(), entity.getTimestamp(), entity.getActeeType(), entity.getActee(), entity.getActeeName()));
	}
}
