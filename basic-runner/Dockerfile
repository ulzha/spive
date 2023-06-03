FROM openjdk:19-jdk-alpine3.15

ENTRYPOINT ["java", \
  "--add-opens=java.base/java.util.regex=ALL-UNNAMED", \
  "io.ulzha.spive.basicrunner.BasicRunner"]

WORKDIR /opt/spive/spive-basic-runner
ENV CLASSPATH=./*
COPY target/dependency .
COPY target/spive-basic-runner.jar .
