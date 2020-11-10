#!/usr/bin/env bash

is_one_of_them_installed() {
  local packages="$1"

  for package in $packages ; do
    dpkg-query --status $package 2> /dev/null 1>&2
    [ $? -eq 0 ] && return 0
  done

  return $?
}


# Check libcore dependencies
while read lib_with_packages ; do

    lib="$(echo "$lib_with_packages" | cut -d ':' -f 1)"
    packages="$(echo "$lib_with_packages" | cut -d ':' -f 2)"

    [ -z "$packages" ] && continue
    is_one_of_them_installed $packages
    if [ $? -ne 0 ] ; then
      echo ""
      echo " * Missing $lib"
      echo "   => Available packages: $packages"
    fi

done