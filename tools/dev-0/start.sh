#!/usr/bin/env bash

set -euxo pipefail

[[ $(which cbt) ]] || { echo "To start dev-0 environment, you need cbt installed"; exit 1; }
[[ $(which curl) ]] || { echo "To start dev-0 environment, you need curl installed"; exit 1; }
[[ $(which docker) ]] || { echo "To start dev-0 environment, you need docker installed"; exit 1; }
[[ $(which jq) ]] || { echo "To start dev-0 environment, you need jq installed"; exit 1; }
[[ $(which mvnd) ]] || { echo "To start dev-0 environment, you need mvnd installed"; exit 1; }

CURL="curl -sSLf --max-time 5"
DC="docker compose -f tools/dev-0/docker-compose.yml"
# speed up Maven
MVN="mvnd -Dfmt.skip=true -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dmvnd.connectTimeout=60s"

# 1. build app as a jar, and more
$MVN clean package dependency:go-offline -am -pl app,basic-runner,tools

# 2. prepare runners and event stores
# (initialize local filesystem)
# (alt. structure the logs so they are mountable directly)
export FS_ARTIFACT_REPO_DIR="$(pwd)/.spive/$USER-dev/dev-0/artifact-repo"
rm -r "$FS_ARTIFACT_REPO_DIR" || true
mkdir -p "$FS_ARTIFACT_REPO_DIR"
export FS_EVENT_STORE_DIR="$(pwd)/.spive/$USER-dev/dev-0/event-store"
rm -r "$FS_EVENT_STORE_DIR" || true
mkdir -p "$FS_EVENT_STORE_DIR"
# (launch docker compose with all the right images)
export BASIC_RUNNER_IMAGE_NAME=$(cat $PWD/basic-runner/target/docker/image-name)
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
# (alt. run SpiveInstance$Main.main with local log, no need to run basic-runner? Doesn't matter much if chicken first or egg first)
cp app/target/spive-0.0.1-SNAPSHOT.jar "$FS_ARTIFACT_REPO_DIR"
mkdir -p "$FS_EVENT_STORE_DIR/93ff4295-5a8c-4181-b50b-3d7345643581"
cp tools/SpiveDevBootstrap.jsonl "$FS_EVENT_STORE_DIR/93ff4295-5a8c-4181-b50b-3d7345643581/events.jsonl"
run_bootstrap_request='{
  "threadGroup": {
    "name": "foo",
    "artifactUrl": "file:///mnt/artifact-repo/spive-0.0.1-SNAPSHOT.jar",
    "mainClass": "io.ulzha.spive.app.spive.gen.SpiveInstance$Main",
    "args": [
      "io.ulzha.spive.core.LocalFileSystemEventStore;basePath=/mnt/event-store",
      "93ff4295-5a8c-4181-b50b-3d7345643581",
      "io.ulzha.spive.core.LocalFileSystemEventStore;basePath=/mnt/event-store",
      "93ff4295-5a8c-4181-b50b-3d7345643581",
      "dev-0",
      "event loop only"
    ]
  }
}'
until
  $CURL -X POST -d "$run_bootstrap_request" -i http://localhost:8429/api/v0/thread_groups
do echo "retrying in 1 s"; sleep 1; done

# 5. expect SpiveDevBootstrap to checkpoint to the end of its log and launch SpiveDev0
until
  heartbeat=$($CURL http://localhost:8429/api/v0/thread_groups/foo/heartbeat)
  echo "$heartbeat" | jq -r .checkpoint | grep "#4"
do echo "retrying in 1 s"; sleep 1; done

# 6. incapacitate dev-bootstrap
$DC pause dev-bootstrap

# 7. initialize SpiveDev0 inventory by copying logs into Bigtable
# (alt. call SpiveDev0 API instead of copying logs, when API is usable)
# (alt. clone some subset of prod-2 or prod-3)
mkdir -p "$FS_EVENT_STORE_DIR/2c543574-f3ac-4b4c-8a5b-a5e188b9bc94"
cp tools/SpiveDev0.jsonl "$FS_EVENT_STORE_DIR/2c543574-f3ac-4b4c-8a5b-a5e188b9bc94/events.jsonl"
copy_args=\
' io.ulzha.spive.core.LocalFileSystemEventStore;basePath='"$FS_EVENT_STORE_DIR"\
' 2c543574-f3ac-4b4c-8a5b-a5e188b9bc94'\
' io.ulzha.spive.core.BigtableEventStore;projectId=user-dev;instanceId=spive-dev-0;hostname=localhost;port=8086'\
' 2c543574-f3ac-4b4c-8a5b-a5e188b9bc94'\
' 2021-11-15T12:00:00Z#2'
$MVN exec:java@copy-event-log -pl tools \
  -Dexec.args="$copy_args"
# expect SpiveDev0 to checkpoint through the log
until
  heartbeat=$($CURL http://localhost:8430/api/v0/thread_groups/85ceafce-ebb3-3a3c-a3e6-46d064c782ab/heartbeat)
  echo "$heartbeat" | jq -r .checkpoint | grep "#2"
do echo "retrying in 1 s"; sleep 1; done

# 8. call API to launch one "hello world" style app
$MVN clean package -f example/clicc-tracc
cp example/clicc-tracc/app/target/spive-example-clicc-tracc-0.0.1-SNAPSHOT.jar "$FS_ARTIFACT_REPO_DIR"
run_clicc_tracc_request='{
  "artifactUrl": "file:///mnt/artifact-repo/spive-example-clicc-tracc-0.0.1-SNAPSHOT.jar;mainClass=io.ulzha.spive.example.clicctracc.app.spive.gen.CliccTraccInstance$Main",
  "availabilityZones": ["dev-1"],
  "inputStreamIds": ["708d2710-3a80-4d40-abb6-3b29a828c289"],
  "outputStreamIds": ["708d2710-3a80-4d40-abb6-3b29a828c289"]
}'
until
  $CURL -X PUT -H "Content-Type: application/json" -d "$run_clicc_tracc_request" -i http://localhost:8440/api/applications/CliccTracc/0.0.1-alpha
do echo "retrying in 1 s"; sleep 1; done
# expect SpiveDev0 to watch the instance and append progress events to the log
until
  heartbeat=$($CURL http://localhost:8430/api/v0/thread_groups/85ceafce-ebb3-3a3c-a3e6-46d064c782ab/heartbeat)
  echo "$heartbeat" | jq -r .checkpoint | grep "#n"
do echo "retrying in 1 s"; sleep 1; done

# 9. print instructions, keep following the output of each container
echo "Press Ctrl+C to stop"
# TODO keep stdout and stderr attached till the end? Now we don't see workload shutdown messages, just Docker's "Aborting on container exit..."
wait $DC_PID
