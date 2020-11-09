#!/usr/bin/env bash
set -euxo pipefail


function is_one_of_them_installed() {
  local packages_with_lib="$*"

  echo $packages_with_lib | while read package ; do
    dpkg-query --status $package 2> /dev/null
    [ $? -eq 0 ] && return 0
  done

  return 1
}


# Check libcore dependencies
if [ -f lib_core.deps ] ; then
  cat lib_core.deps | while read lib_with_packages ; do

    is_one_of_them_installed $packages
    if [ $? -ne 0 ] ; then
      echo "Missing lib! None of the following packages installed : $packages"
      exit 1
    fi

  done
fi


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

