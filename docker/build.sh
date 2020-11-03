#!/usr/bin/env bash
set -euxo pipefail

apt-get update
apt-get install -qy apt-transport-https software-properties-common gnupg
apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
add-apt-repository "deb https://dl.bintray.com/sbt/debian /"
apt-get update && apt-get install -qy sbt

cp src/main/resources/application.conf.sample src/main/resources/application.conf
sbt clean stage -mem 1000
exit 0
