#!/usr/bin/env bash

set -euxo pipefail

[[ $(which cbt) ]] || { echo "To start dev-0 environment, you need cbt installed"; exit 1; }
[[ $(which curl) ]] || { echo "To start dev-0 environment, you need curl installed"; exit 1; }
[[ $(which docker) ]] || { echo "To start dev-0 environment, you need docker installed"; exit 1; }
[[ $(which jq) ]] || { echo "To start dev-0 environment, you need jq installed"; exit 1; }
[[ $(which mvnd) ]] || { echo "To start dev-0 environment, you need mvnd installed"; exit 1; }

DC="docker compose -f tools/dev-0/docker-compose.yml"
# speed up Maven
MVN="mvnd -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dsurefire.skip=true -Dfmt.skip=true"

# 1. build app as a jar, and more
$MVN clean package dependency:go-offline -am -pl app,thread-runner,tools

# 2. prepare runners and event stores
# (initialize local filesystem)
# (alt. structure the logs so they are mountable directly)
rm -r event-store || true
mkdir -p event-store
# (launch docker compose with all the right images)
export THREAD_RUNNER_IMAGE_NAME=$(cat $PWD/thread-runner/target/docker/image-name)
$DC down -v
$DC up --abort-on-container-exit &
DC_PID=$!
# (might want to wait here until healthy, for less confusing intermingled console output, dunno)
# (initialize bigtable emulator)
export BIGTABLE_EMULATOR_HOST=localhost:8086
until
  cbt -project user-dev -instance spive-dev-0 createtable event-store
  cbt -project user-dev -instance spive-dev-0 createfamily event-store event
  cbt -project user-dev -instance spive-dev-0 setgcpolicy event-store event maxversions=1
do echo "retrying in 1 s"; sleep 1; done

# 4. launch SpiveDevBootstrap with local log
# (alt. run SpiveInstance$Main.main with local log, no need to run thread-runner? Doesn't matter much if chicken first or egg first)
mkdir -p event-store/93ff4295-5a8c-4181-b50b-3d7345643581
cp tools/SpiveDevBootstrap.jsonl event-store/93ff4295-5a8c-4181-b50b-3d7345643581/events.jsonl
until
  curl -sSLf -X PUT -d '{"threadGroup": {"name": "foo", "artifactUrl": "file:///app/target/spive-0.0.1-SNAPSHOT.jar", "mainClass": "io.ulzha.spive.app.lib.SpiveInstance$Main", "args": ["io.ulzha.spive.core.LocalFileSystemEventStore;", "93ff4295-5a8c-4181-b50b-3d7345643581", "io.ulzha.spive.core.LocalFileSystemEventStore;", "93ff4295-5a8c-4181-b50b-3d7345643581", "dev-0", "event loop only"]}}' -i http://localhost:8079/api/v0/thread_groups
do echo "retrying in 1 s"; sleep 1; done

# 5. expect SpiveDevBootstrap to checkpoint to the end of its log and launch SpiveDev0
until
  heartbeat=$(curl -sSLf http://localhost:8079/api/v0/thread_groups/foo/heartbeat)
  echo "$heartbeat" | jq -r .checkpoint | grep "#4"
do echo "retrying in 1 s"; sleep 1; done

# 6. incapacitate dev-bootstrap
$DC pause dev-bootstrap

# 7. initialize SpiveDev0 inventory by copying logs into Bigtable
# (alt. call SpiveDev0 API instead of copying logs, when API is usable)
# (alt. clone some subset of prod-2 or prod-3)
mkdir -p event-store/2c543574-f3ac-4b4c-8a5b-a5e188b9bc94
cp tools/SpiveDev0.jsonl event-store/2c543574-f3ac-4b4c-8a5b-a5e188b9bc94/events.jsonl
$MVN exec:java@copy-event-log -pl tools \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=WARN \
  -Dexec.args="io.ulzha.spive.core.LocalFileSystemEventStore; 2c543574-f3ac-4b4c-8a5b-a5e188b9bc94 io.ulzha.spive.core.BigtableEventStore;projectId=user-dev;instanceId=spive-dev-0;hostname=localhost;port=8086 2c543574-f3ac-4b4c-8a5b-a5e188b9bc94 2021-11-15T12:00:00.000Z#3"

# 8. expect SpiveDev0 to checkpoint ahead
# (alt. expect API calls to succeed)
until
  heartbeat=$(curl -sSLf http://localhost:8080/api/v0/thread_groups/ff4726aa-9f71-49e2-b139-d71d780a817c/heartbeat)
  echo "$heartbeat" | jq -r .checkpoint | grep "#3"
do echo "retrying in 1 s"; sleep 1; done

# 9. print instructions, keep following the output of each container
echo "Press Ctrl+C to stop"
# TODO tear down if anything fails
wait $DC_PID
