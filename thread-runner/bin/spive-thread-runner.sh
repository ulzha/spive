#!/bin/sh
exec /opt/openjdk-19/bin/java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -jar /usr/share/spive-thread-runner/service.jar \
  "$@"
