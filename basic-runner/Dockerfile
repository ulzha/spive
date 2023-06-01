FROM openjdk:19-jdk-alpine3.15

ENTRYPOINT ["/usr/bin/spive-basic-runner.sh"]

COPY bin/spive-basic-runner.sh /usr/bin/spive-basic-runner.sh
COPY target/lib /usr/share/spive-basic-runner/lib
COPY target/spive-basic-runner.jar /usr/share/spive-basic-runner/service.jar
