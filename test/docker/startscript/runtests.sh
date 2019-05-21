#!/bin/bash -xe

# Test Setup
SUBJECT=../../../docker/data/promregator.sh
CURDIR=`pwd`

# The script hard-writes to /home/promregator; we have to make sure that this folder exists
mkdir -p /home/promregator

# First Test: Straight forward case
export JAVACMD=$CURDIR/validator1.sh
$SUBJECT


# Second Test: With Encrypt Key
echo -n "Test Key With Spaces" > /run/secrets/encrypt_key_file
export JAVACMD=$CURDIR/validator2.sh
export ENCRYPT_KEY_FILE=encrypt_key_file
$SUBJECT


