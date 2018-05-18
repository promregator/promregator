#!/bin/bash -xe

JAVACMD=/usr/bin/java

if [ "$JAVA_MEM_OPTS" == "" ]; then
	CGROUPS_LIMIT=`cat /sys/fs/cgroup/memory/memory.limit_in_bytes`
	TOTALMEM=$(awk '/^MemTotal:/{print $2}' /proc/meminfo)
	TOTALMEM=$((TOTALMEM * 1024))
	
	echo "CGroups memory limit:  $GROUPS_LIMIT"
	echo "maximal RAM available: $TOTALMEM"
	
	if [ $CGROUPS_LIMIT -gt $TOTALMEM ]; then
		MEM=$TOTALMEM
		SET_MS=0
	else
		MEM=$CGROUPS_LIMIT
		SET_MS=1
	fi
	echo "Using limit of $MEM"
	
	JVMMAXHEAP=$((MEM - 540 * 1024 * 1024))
	
	if [ $JVMMAXHEAP -lt 200000000 ]; then
		echo "The memory limit you have specified is not sufficient for operating Promregator; at least 200MB of heap memory (~750MB system memory) is required!"
		exit 1
	fi
	
	JAVA_HEAP_OPTS="-Xmx$JVMMAXHEAP"
	if [ $SET_MS == 1 ]; then
		JAVA_HEAP_OPTS+=" -Xms$JVMMAXHEAP"
	fi
	
	JAVA_MEM_OPTS="-XX:+PrintFlagsFinal $JAVA_HEAP_OPTS -Xss600k -XX:ReservedCodeCacheSize=256m -XX:MaxMetaspaceSize=300m"
fi
echo "Memory configuration to be used: $JAVA_MEM_OPTS"

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

