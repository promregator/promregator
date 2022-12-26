package org.cloudfoundry.promregator.messagebus;

public interface MessageSubscriber {
	void receiveMessage(String topic, Object message);
}
