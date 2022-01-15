#!/bin/bash

# see also https://stackoverflow.com/a/5257398
IN=`openssl dgst -hex "$1" "$2"`
HASHV=(${IN//= / })
echo ${HASHV[1]}

