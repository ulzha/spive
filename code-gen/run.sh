#!/usr/bin/env bash

set -euxo pipefail

[[ $(which mvnd) ]] || { echo "To run code-gen, you need mvnd installed"; exit 1; }

mvnd compile exec:java@generate-ioc-code -pl code-gen
mvnd com.spotify.fmt:fmt-maven-plugin:format
mvnd com.spotify.fmt:fmt-maven-plugin:format -f example/clicc-tracc
mvnd com.spotify.fmt:fmt-maven-plugin:format -f example/copy
