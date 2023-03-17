#!/bin/bash -xe

if [ "$JAVACMD" == "" ]; then
	# Note: this supports mocking for our test environment of this script
	JAVACMD=java
fi

if [ "$JAVA_MEM_OPTS" == "" ]; then
	JAVA_MEM_OPTS="-XX:+PrintFlagsFinal -Xss600k -XX:ReservedCodeCacheSize=256m -XX:MaxMetaspaceSize=300m"
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


# Configuring ENCRYPT_KEY from docker secrets (if available) - see also #88 and #62
if [ -d /run/secrets ]; then
	ls -al /run/secrets/
fi

set +x
if [ "$ENCRYPT_KEY_FILE" != "" ]; then
	export ENCRYPT_KEY="`cat /run/secrets/$ENCRYPT_KEY_FILE`"
	if [ "$ENCRYPT_KEY" != "" ]; then
		echo "ENCRYPT_KEY is set"
	fi
fi
set -x

RUNNING=true
while [ "$RUNNING" == "true" ]; do
	$JAVACMD $JAVA_MEM_OPTS $JAVA_OPTS -Dspring.config.name=promregator -jar /opt/promregator/promregator.jar
	
	ret=$?
	if [ "$ret" == "161" ]; then
		echo "Restart requested by Promregator (triggered by CF Watchdog)"
		continue
	elif [ "$ret" == "162" ]; then
		echo "Restart requested by Promregator (triggered due to OutOfMemoryError identified by Promregator)"
		continue
	elif [ "$ret" == "127" ]; then
		echo "Restart requested by Promregator (triggered due to OutOfMemoryError identified by JVM)"
		continue
	else
		RUNNING=false
	fi
done
