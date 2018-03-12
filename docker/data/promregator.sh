#!/bin/bash -xe

JAVACMD=/usr/bin/java

if [ "$JAVA_MEM_OPTS" == "" ]; then
	JAVA_MEM_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+PrintFlagsFinal -XX:+PrintGCDetails"
fi

if [ "$JAVA_OPTS" == "" ]; then
	JAVA_OPTS=""
fi

if [ "$PROMREGATOR_CONFIG_FILE" == "" ]; then
	PROMREGATOR_CONFIG_FILE="/etc/promregator/promregator.yml"
fi

$JAVACMD $JAVA_MEM_OPTS $JAVA_OPTS "-Dspring.config.location=$PROMREGATOR_CONFIG_FILE" -jar /opt/promregator/promregator.jar

