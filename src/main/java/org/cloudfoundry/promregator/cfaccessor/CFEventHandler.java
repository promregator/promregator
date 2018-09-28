package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedList;

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

	private HashSet<String> eventsAlreadySeen = new HashSet<>();
	private LinkedList<EventResource> eventsSequence = new LinkedList<>();
	
	private boolean firstExecution = true;
	
	@Scheduled(fixedDelay=10000)
	public void periodRequest() throws ConfigurationException {
		Instant now = Instant.now();
		
		Mono<ListEventsResponse> res = this.cfAccessor.retrieveEvents(this.lastRefreshed.minusSeconds(30));
		Flux<EventResource> rawEventFlux = res.doOnSuccess(r -> {
			log.debug(String.format("Starting to scan now; fetching events since %s", this.lastRefreshed.toString()));
			// ensure that we don't fetch the same stuff twice
			this.lastRefreshed = now;
		}).map(r -> r.getResources())
		.flatMapMany(list -> Flux.fromIterable(list));
		
		if (this.firstExecution) {
			// on the first iteration, we just want to load the set of events to initialize the deduplication mechanism
			rawEventFlux.doOnComplete(() -> {
				log.debug(String.format("Scan complete: new timestamp %s", this.lastRefreshed.toString()));
			}).subscribe(event -> {
				this.eventsAlreadySeen.add(event.getMetadata().getId());
				this.eventsSequence.addFirst(event);
			});
			
			this.firstExecution = false;
		} else {
			/* filter out duplicate events already sent to us */
			rawEventFlux.filter(event -> !this.eventsAlreadySeen.contains(event.getMetadata().getId()))
			.doOnComplete(() -> {
				log.debug(String.format("Scan complete: new timestamp %s", this.lastRefreshed.toString()));
			}).subscribe(event -> {
				this.processEvent(event);
				
				this.eventsAlreadySeen.add(event.getMetadata().getId());
				this.eventsSequence.addFirst(event);
			});
			
			this.cleanupDedouplicationFacilities();
		}
	}

	private void cleanupDedouplicationFacilities() {
		Instant cutoff = Instant.now().minusSeconds(60*30);
		
		if (this.eventsSequence.isEmpty()) {
			return;
		}
		
		EventResource last = this.eventsSequence.getLast();
		Instant lastTimestamp = Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC")).parse(last.getEntity().getTimestamp()));
		
		while (Duration.between(cutoff, lastTimestamp).isNegative()) {
			this.eventsSequence.remove(last);
			this.eventsAlreadySeen.remove(last.getMetadata().getId());

			if (this.eventsSequence.isEmpty()) {
				return;
			}
			
			last = this.eventsSequence.getLast();
			lastTimestamp = Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC")).parse(last.getEntity().getTimestamp()));
		}
	}

	private void processEvent(EventResource event) {
		EventEntity entity = event.getEntity();
		
		log.info(String.format("Event %s raised: type=%s on %s, happened to %s/%s/%s", event.getMetadata().getId(), 
				entity.getType(), entity.getTimestamp(), entity.getActeeType(), entity.getActee(), entity.getActeeName()));
	}
}
