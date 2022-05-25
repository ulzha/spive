FROM adoptopenjdk/openjdk11:x86_64-alpine-jdk-11.0.4_11

ENTRYPOINT ["/usr/bin/spive-thread-runner.sh"]

COPY bin/spive-thread-runner.sh /usr/bin/spive-thread-runner.sh
COPY target/lib /usr/share/spive-thread-runner/lib
COPY target/spive-thread-runner.jar /usr/share/spive-thread-runner/service.jar
