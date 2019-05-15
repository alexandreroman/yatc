#!/bin/sh
MODULES="yatc-*"
for i in $MODULES; do cd "$i" && ./mvnw clean package && cd -; done
