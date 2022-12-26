package org.cloudfoundry.promregator.messagebus;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageBus {

	private static final Logger log = LoggerFactory.getLogger(MessageBus.class);

	private Map<String, List<MessageSubscriber>> topicSubscriberMap = Collections.synchronizedMap(new HashMap<String, List<MessageSubscriber>>());
	
	public void subscribe(String topic, MessageSubscriber subscriber) {
		List<MessageSubscriber> list = this.topicSubscriberMap.computeIfAbsent(topic, key -> Collections.synchronizedList(new LinkedList<>()));
		if (!list.contains(subscriber)) {
			list.add(subscriber);
		}
	}
	
	public void unsubscribe(String topic, MessageSubscriber subscriber) {
		this.topicSubscriberMap.get(topic).remove(subscriber);
	}
	
	public void notifyEvent(String topic, Object message) {
		final List<MessageSubscriber> subscriberList = this.topicSubscriberMap.get(topic);
		
		subscriberList.forEach(subscriber -> {
			try {
				subscriber.receiveMessage(topic, message);
			} catch (Throwable t) {
				log.error("Subscriber {} threw unexpected throwable on message notification for topic {}; continuing, but this is most likely a bug!", subscriber, topic, t);
			}
		});
		
	}
}
