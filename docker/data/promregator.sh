#!/bin/bash -xe

JAVACMD=/usr/bin/java

if [ "$JAVA_MEM_OPTS" == "" ]; then
	JAVA_MEM_OPTS="-XX:+PrintFlagsFinal -Xmx300m -Xms300m -Xss600k -XX:ReservedCodeCacheSize=256m -XX:MaxMetaspaceSize=300m"
fi

if [ "$JAVA_OPTS" == "" ]; then
	JAVA_OPTS=""
fi

if [ "$PROMREGATOR_CONFIG_DIR" == "" ]; then
	PROMREGATOR_CONFIG_DIR="/etc/promregator"
fi

# Why /home/ and not /opt/? see https://github.com/promregator/promregator/issues/39
cd /home/promregator

# Workaround for not-working spring.config.location
ln -sf ../../$PROMREGATOR_CONFIG_DIR/promregator.yml .
ln -sf ../../$PROMREGATOR_CONFIG_DIR/promregator.properties .

$JAVACMD $JAVA_MEM_OPTS $JAVA_OPTS -Dspring.config.name=promregator -jar /opt/promregator/promregator.jar

