#!/usr/bin/env bash
set -euxo pipefail

cp src/main/resources/application.conf.sample src/main/resources/application.conf
sbt clean stage -mem 1000
exit 0
