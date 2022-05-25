#!/bin/sh
exec /opt/java/openjdk/bin/java \
  -jar /usr/share/spive-thread-runner/service.jar \
  "$@"
