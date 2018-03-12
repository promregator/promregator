#!/bin/bash -xe

JAVACMD=/usr/bin/java

if [ "$JAVA_MEM_OPTS" == "" ]; then
	JAVA_MEM_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+PrintFlagsFinal -XX:+PrintGCDetails"
fi

if [ "$JAVA_OPTS" == "" ]; then
	JAVA_OPTS=""
fi

$JAVACMD $JAVA_MEM_OPTS $JAVA_OPTS -jar /opt/promregator/promregator.jar

