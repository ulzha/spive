FROM openjdk:19-jdk-alpine3.15

ENTRYPOINT ["/usr/bin/spive-thread-runner.sh"]

COPY bin/spive-thread-runner.sh /usr/bin/spive-thread-runner.sh
COPY target/lib /usr/share/spive-thread-runner/lib
COPY target/spive-thread-runner.jar /usr/share/spive-thread-runner/service.jar
