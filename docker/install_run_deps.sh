#!/usr/bin/env bash
set -euxo pipefail

mkdir -p /app/database # for sqlite (can be mount outside the container at runlevel)

# Debug tools untils we have our ledger-stretch-slim image
apt-get update && apt-get install -yq curl netcat iputils-ping iproute2 lsof procps

# Debug tools untils we have our ledger-stretch-slim image
apt-get install -yq curl netcat iputils-ping iproute2 lsof procps

# Needed when activating PG Support on WD
apt-get install -yq libpq-dev

# Cleanup
apt-get clean
rm -rf -- /var/lib/apt/lists/*
exit 0
