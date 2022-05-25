#!/usr/bin/env bash

set -euxo pipefail

docker-compose -f tools/dev-0/docker-compose.yml down --volumes
