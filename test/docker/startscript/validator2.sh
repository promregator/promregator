#!/bin/bash

echo "Received command line $*"

if [ "$*" != "-XX:+PrintFlagsFinal -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Xss600k -XX:ReservedCodeCacheSize=256m -XX:MaxMetaspaceSize=300m -Dspring.config.name=promregator -jar /opt/promregator/promregator.jar" ]; then
	echo "Test failed"
	exit 1
fi

if [ "$ENCRYPT_KEY" != "Test Key With Spaces" ]; then
	echo "Encrypt key is not set properly"
	exit 1
fi

echo "Tests ok"

# Indicate success
exit 0