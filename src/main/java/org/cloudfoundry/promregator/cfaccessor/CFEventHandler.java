package org.cloudfoundry.promregator.cfaccessor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.cloudfoundry.client.v2.events.EventEntity;
import org.cloudfoundry.client.v2.events.EventResource;
import org.cloudfoundry.client.v2.events.ListEventsResponse;
import org.cloudfoundry.promregator.config.ConfigurationException;
import org.cloudfoundry.promregator.messagebus.MessageBusDestination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CFEventHandler {
	private static final Logger log = Logger.getLogger(CFEventHandler.class);
	
	private static final Object DUMMY_OBJECT_FOR_JMS = new Object();
	
	@Autowired
	private CFAccessor cfAccessor;
	
	@Autowired
	private JmsTemplate jmsTemplate;
	
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
		
		log.info(String.format("Event %s raised: type=%s on %s impacting %s/%s/%s", event.getMetadata().getId(), 
				entity.getType(), entity.getTimestamp(), entity.getActeeType(), entity.getActee(), entity.getActeeName()));
		
		/* ************************** Organization-related events ************************** */
		if (entity.getType().equals("audit.organization.create")) {
			/*
			 * a new org was created
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_ORG_CREATED, entity.getOrganizationId());
		}
		
		if (entity.getType().equals("audit.organization.delete-request")) {
			/*
			 * a new org was deleted
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_ORG_DELETED, entity.getOrganizationId());
		}
		
		if (entity.getType().equals("audit.organization.update")) {
			/*
			 * a new org was changed
			 */
			/* Warning! entity.getActeeName() contains the *new* name of the org only!
			 * Thus, we cannot provide the old name and hence we cannot send a org name with the event.
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_ORG_CHANGED, DUMMY_OBJECT_FOR_JMS);
		}
		
		
		/* ************************** Space-related events ************************** */
		
		if (entity.getActeeType().equals("space") && entity.getType().equals("audit.space.create")) {
			/*
			 * a new space was created
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_SPACE_CREATED, entity.getSpaceId());
		}
		
		if (entity.getActeeType().equals("space") && entity.getType().equals("audit.space.delete-request")) {
			/*
			 * a new space was deleted
			 */
			CacheKeySpace key = new CacheKeySpace(entity.getOrganizationId(), entity.getActeeName());
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_SPACE_DELETED, key);
		}
		
		if (entity.getActeeType().equals("space") && entity.getType().equals("audit.space.update")) {
			/*
			 * a new space was changed
			 */
			
			/* Warning! entity.getActeeName() contains the *new* name of the space only!
			 * Thus, we cannot provide the old name and hence we cannot send a CacheKeySpace with the event.
			 * The best thing what we have is to send at least the organization id... (partial key)
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_SPACE_CHANGED, entity.getOrganizationId());
		}
		
		
		/* ************************** Application-related events ************************** */

		if (entity.getActeeType().equals("app") && entity.getType().equals("audit.app.create") ) {
			/*
			 * a new application was created in the space
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_APP_CREATED, entity.getSpaceId());
		}
		
		if (entity.getActeeType().equals("app") && entity.getType().equals("audit.app.delete-request") ) {
			/*
			 * a new application was deleted in the space
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_APP_DELETED, entity.getSpaceId());
		}
		
		if (entity.getActeeType().equals("app") && 
				(entity.getType().equals("audit.app.update") || entity.getType().equals("audit.app.stop") || entity.getType().equals("audit.app.start") )
			) {
			/* 
			 * something about the application itself was changed.
			 * Possible changes might be:
			 * - stop of application
			 * - start of application
			 * - change of number of instances
			 * - change of name of application
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_APP_CHANGED, entity.getSpaceId());
			// Note: The parameter is the space ID in which the application is located
			
			/* Note that also the name of the application could have changed!
			 * The name of the application, however, is subject to the Target Resolver.
			 */
			Optional<Object> newName = entity.getMetadatas().get("name");
			if (newName.isPresent()) {
				// this is a name change!
				this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_APP_NAME_CHANGED, DUMMY_OBJECT_FOR_JMS);
			}
		}
		
		if (entity.getActeeType().equals("app") && 
				(entity.getType().equals("audit.app.map-route")  || entity.getType().equals("audit.app.unmap-route"))
				) {
			/*
			 * something might have changed in the hostname / map of routes of an application.
			 * Possible changes might be:
			 * - mapping of a new route (application becomes accessible)
			 * - unmapping of an existing route (application may become inaccessible)
			 * 
			 * Keep in mind that the hostname of a route may not be changed. The achieve the same for an application 
			 * a new route would have to be created and a "map-route" and an "unmap-route" event must be sent instead
			 */
			this.jmsTemplate.convertAndSend(MessageBusDestination.CF_EVENT_ROUTE_TO_APP_CHANGED, entity.getSpaceId());
		}
		
	}
}
